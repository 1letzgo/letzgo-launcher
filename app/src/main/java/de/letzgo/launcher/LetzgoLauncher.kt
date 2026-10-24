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
import com.android.launcher3.folder.Folder
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
import de.letzgo.launcher.system.SystemPanels
import de.letzgo.launcher.update.AppUpdate
import de.letzgo.launcher.update.AppUpdateDialog
import java.util.stream.Stream

/**
 * Launcher-Activity mit iOS-Verhalten: kein App-Drawer, alle Apps liegen auf den
 * Homescreen-Seiten, Bearbeitungsmodus mit Wackeln ([JiggleMode]), App-Mediathek rechts,
 * Heute-Seite links und Wischen nach unten für Mitteilungen/Schnelleinstellungen
 * ([PanelSwipeController]).
 */
class LetzgoLauncher : Launcher() {

    /** Lazy, weil Launcher3 die Touch-Controller schon während `setupViews()` anlegt. */
    val jiggleMode by lazy { JiggleMode(this) }
    private val appLibrary by lazy {
        AppLibraryPanel(this)
    }
    private val today by lazy { TodayPanel(this, ::openSearch) }
    private val search by lazy {
        SearchOverlay(this)
    }
    private lateinit var dock: DockBackground
    private var homeDimmedForFolder = false

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
        dock = DockBackground(this).apply { onCapsuleChanged = ::layoutDockAccessories }
        dock.attach()
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

    /**
     * Hinter einem offenen Ordner tritt der Homescreen zurück, damit durch den halbtransparenten
     * Ordner nur das Hintergrundbild scheint und keine Icons oder Beschriftungen.
     */
    override fun onDragLayerHierarchyChanged() {
        super.onDragLayerHierarchyChanged()
        val folderOpen = Folder.getOpen(this) != null
        if (folderOpen == homeDimmedForFolder) return
        homeDimmedForFolder = folderOpen
        val alpha = if (folderOpen) 0f else 1f
        listOf(workspace, hotseat, workspace.pageIndicator as View).forEach {
            it.animate().alpha(alpha).setDuration(FOLDER_FADE_MS).start()
        }
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

    /** Seitenpunkte über dem Dock bleiben sichtbar und sind immer weiß (Launcher3 blendet sie aus). */
    private fun showPageDots() {
        workspace.pageIndicator.setShouldAutoHide(false)
        workspace.pageIndicator.setPaintColor(Color.WHITE)
    }

    /**
     * Wischen nach unten wie auf iOS: in der linken Bildschirmhälfte die Mitteilungen, in der
     * rechten die Schnelleinstellungen.
     */
    private fun onSwipeDown(x: Float) {
        if (x < dragLayer.width / 2f) {
            SystemPanels.openNotifications(this)
        } else {
            SystemPanels.openQuickSettings(this)
        }
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
     * Seitenpunkte liegen mittig über der Dock-Kapsel. Launcher3 setzt die Punkte
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
                onSwipeDown = ::onSwipeDown,
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
        const val DOCK_ACCESSORY_GAP_DP = 20f
        const val FOLDER_FADE_MS = 150L
    }
}
