package de.letzgo.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.Launcher
import com.android.launcher3.DropTarget
import com.android.launcher3.LauncherState
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.Popup
import com.android.launcher3.popup.PopupController
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.popup.SystemShortcut.APP_INFO
import com.android.launcher3.popup.SystemShortcut.INSTALL
import com.android.launcher3.popup.SystemShortcut.WIDGETS
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.TouchController
import de.letzgo.launcher.edit.JiggleMode
import de.letzgo.launcher.edit.JiggleTouchController
import de.letzgo.launcher.model.HomeScreenApps
import de.letzgo.launcher.panels.AppLibraryPanel
import de.letzgo.launcher.panels.PanelSwipeController
import de.letzgo.launcher.panels.SearchOverlay
import de.letzgo.launcher.panels.TodayPanel
import de.letzgo.launcher.popup.EditHomeShortcut
import de.letzgo.launcher.popup.RemoveAppShortcut
import de.letzgo.launcher.ui.DockBackground
import de.letzgo.launcher.ui.SearchPill
import de.letzgo.launcher.update.AppUpdate
import de.letzgo.launcher.update.AppUpdateDialog
import java.util.stream.Stream

/**
 * Launcher-Activity mit iOS-Verhalten: kein App-Drawer, alle Apps liegen auf den
 * Homescreen-Seiten, Bearbeitungsmodus mit Wackeln ([JiggleMode]), App-Mediathek rechts,
 * Heute-Ansicht links und Suche ([PanelSwipeController]).
 */
class LetzgoLauncher : Launcher() {

    /** Lazy, weil Launcher3 die Touch-Controller schon während `setupViews()` anlegt. */
    val jiggleMode by lazy { JiggleMode(this) { onJiggleChanged() } }
    private val appLibrary by lazy {
        AppLibraryPanel(this).apply { onVisibilityChanged = { updateSearchPill() } }
    }
    private val today by lazy { TodayPanel(this).apply { onVisibilityChanged = { updateSearchPill() } } }
    private val search by lazy {
        SearchOverlay(this).apply { onVisibilityChanged = { updateSearchPill() } }
    }
    private val searchPill by lazy { SearchPill(this) { openSearch() } }
    private lateinit var dock: DockBackground

    private val syncAllApps = Runnable { HomeScreenApps.sync(this) }
    private val appsUpdateListener = AllAppsStore.OnUpdateListener(::scheduleSync)
    private var updateDialog: AppUpdateDialog? = null
    private var checkingForUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Feuert nach dem Laden und bei jeder Paketänderung
        appsView.appsStore.addUpdateListener(appsUpdateListener)
    }

    override fun onResume() {
        super.onResume()
        updateDialog?.onResume() ?: checkForUpdate()
    }

    /** Neue Version auf GitHub? [AppUpdate.checkOnLaunch] fragt höchstens alle sechs Stunden. */
    private fun checkForUpdate() {
        if (checkingForUpdate) return
        checkingForUpdate = true
        THREAD_POOL_EXECUTOR.execute {
            val release = AppUpdate.checkOnLaunch(this)
            MAIN_EXECUTOR.execute {
                checkingForUpdate = false
                if (release != null && updateDialog == null && !isDestroyed) {
                    updateDialog = AppUpdateDialog(this, release) { updateDialog = null }.also { it.show() }
                }
            }
        }
    }

    override fun onDestroy() {
        appsView.appsStore.removeUpdateListener(appsUpdateListener)
        dragLayer.removeCallbacks(syncAllApps)
        super.onDestroy()
    }

    override fun setupViews() {
        super.setupViews()
        dock = DockBackground(hotseat).apply { onCapsuleChanged = ::layoutDockAccessories }
        dock.attach()
        searchPill.attach()
        showPageDots()
        (workspace.pageIndicator as View).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutDockAccessories()
        }
        hideDropTargetBar()
        // Wer ein Element zieht, landet wie auf iOS im Bearbeitungsmodus
        dragController.addDragListener(
            object : DragController.DragListener {
                override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) =
                    jiggleMode.enter()

                override fun onDragEnd() {}
            }
        )
    }

    /** Statt „Entfernen“/„Deinstallieren“ beim Ziehen gibt es Minus-Badges ([JiggleMode]). */
    private fun hideDropTargetBar() {
        dragController.removeDragListener(dropTargetBar)
        for (target in dropTargetBar.dropTargets) {
            dragController.removeDragListener(target)
            dragController.removeDropTarget(target)
        }
    }

    /** Langes Drücken auf eine freie Stelle startet den Bearbeitungsmodus statt des Menüs. */
    override fun showDefaultOptions(x: Float, y: Float) = jiggleMode.enter()

    /** Im Bearbeitungsmodus starten Apps nicht; Ordner lassen sich weiter öffnen. */
    override fun getItemOnClickListener() =
        View.OnClickListener { v ->
            if (jiggleMode.isActive && v.tag !is FolderInfo) return@OnClickListener
            ItemClickHandler.INSTANCE.onClick(v)
        }

    /** Im Bearbeitungsmodus kein Kontextmenü beim Anheben. */
    override fun getPopupControllerForAppIcons(): PopupController<Launcher> =
        if (jiggleMode.isActive) NoPopup else super.getPopupControllerForAppIcons()

    override fun getPopupControllerForHomeScreenItems(): PopupController<Launcher> =
        if (jiggleMode.isActive) NoPopup else super.getPopupControllerForHomeScreenItems()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home-Taste beendet den Bearbeitungsmodus und schließt Mediathek, Heute-Ansicht und Suche
        if (Intent.ACTION_MAIN == intent.action) {
            jiggleMode.exit()
            closeOverlays(animate = true)
        }
    }

    override fun onStop() {
        jiggleMode.exit()
        super.onStop()
    }

    override fun onStateSetStart(state: LauncherState) {
        super.onStateSetStart(state)
        showPageDots()
    }

    /**
     * Seitenpunkte sind weiß. Im Ruhezustand liegt an ihrer Stelle die „Suchen“-Pille, die Punkte
     * erscheinen nur beim Blättern; im Bearbeitungsmodus bleiben sie sichtbar.
     */
    private fun showPageDots() {
        val editing = jiggleMode.isActive
        workspace.pageIndicator.setShouldAutoHide(!editing)
        workspace.pageIndicator.setPaintColor(Color.WHITE)
    }

    private fun onJiggleChanged() {
        updateSearchPill()
        showPageDots()
    }

    /** Die Pille gibt es nur auf dem ruhenden Homescreen. */
    private fun updateSearchPill() {
        searchPill.setEnabled(
            !jiggleMode.isActive && !appLibrary.isOpen && !today.isOpen && !search.isOpen
        )
    }

    private fun openSearch() {
        if (jiggleMode.isActive) return
        search.open()
    }

    /** Offene Mediathek, Heute-Ansicht oder Suche schließen, z. B. bei der Home-Taste. */
    private fun closeOverlays(animate: Boolean) {
        appLibrary.close(animate)
        today.close(animate)
        search.close()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!today.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * Seitenpunkte und „Suchen“-Pille liegen mittig über der Dock-Kapsel. Launcher3 setzt die Punkte
     * direkt aufs Dock ([com.android.launcher3.Workspace] `setPageIndicatorInset`), deshalb wird
     * ihr Abstand nach jedem Neusetzen korrigiert.
     */
    private fun layoutDockAccessories() {
        val capsuleTop = dock.capsuleTop ?: return
        val indicator = workspace.pageIndicator as View
        val centerY = hotseat.top + capsuleTop - DOCK_ACCESSORY_GAP_DP * resources.displayMetrics.density
        val lp = indicator.layoutParams as ViewGroup.MarginLayoutParams
        val wanted = (dragLayer.height - centerY - indicator.height / 2f).toInt()
        if (indicator.height > 0 && lp.bottomMargin != wanted) {
            lp.bottomMargin = wanted
            indicator.post { indicator.layoutParams = lp }
        }
        searchPill.placeAt(dragLayer.width / 2f, centerY)
    }

    private fun scheduleSync() {
        dragLayer.removeCallbacks(syncAllApps)
        dragLayer.postDelayed(syncAllApps, SYNC_DELAY_MS)
    }

    /**
     * Ohne [com.android.launcher3.touch.AllAppsSwipeController]: Hochwischen öffnet keinen Drawer.
     * Der [DragController] steht vorn, weil er sich den Startpunkt jeder Geste merkt.
     */
    override fun createTouchControllers(): Array<TouchController> =
        arrayOf(
            dragController,
            JiggleTouchController(this, jiggleMode),
            PanelSwipeController(
                this,
                left = { today },
                right = { appLibrary },
                isBlocked = { jiggleMode.isActive || appLibrary.isOpen || today.isOpen || search.isOpen },
                onSwipeDown = ::openSearch,
            ),
        )

    /** Wie Launcher3, aber „Entfernen“ fragt bei Apps nach Löschen oder Vom-Homescreen-Entfernen. */
    override fun getSupportedShortcuts(itemInfo: ItemInfo): Stream<SystemShortcut.Factory<*>> {
        val inHome = itemInfo.container == CONTAINER_DESKTOP || itemInfo.container == CONTAINER_HOTSEAT
        if (!inHome || itemInfo.itemType != ITEM_TYPE_APPLICATION) {
            return super.getSupportedShortcuts(itemInfo)
        }
        return Stream.of(APP_INFO, WIDGETS, INSTALL, EditHomeShortcut.FACTORY, RemoveAppShortcut.FACTORY)
    }

    /** Popup-Controller, der nichts anzeigt (Bearbeitungsmodus). */
    private object NoPopup : PopupController<Launcher> {
        override fun show(view: View): Popup? = null

        override fun dismiss() {}
    }

    private companion object {
        const val SYNC_DELAY_MS = 300L
        const val DOCK_ACCESSORY_GAP_DP = 24f
    }
}
