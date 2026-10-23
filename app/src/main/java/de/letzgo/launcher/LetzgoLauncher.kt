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
import de.letzgo.launcher.model.AllAppsOnHomeScreen
import de.letzgo.launcher.model.HomeScreenRemovals
import de.letzgo.launcher.popup.EditHomeShortcut
import de.letzgo.launcher.popup.RemoveAppShortcut
import de.letzgo.launcher.ui.DockBackground
import de.letzgo.launcher.update.AppUpdate
import de.letzgo.launcher.update.AppUpdateDialog
import java.util.stream.Stream

/**
 * Launcher-Activity mit iOS-Verhalten: kein App-Drawer, alle Apps liegen auf den
 * Homescreen-Seiten, Bearbeitungsmodus mit Wackeln ([JiggleMode]).
 */
class LetzgoLauncher : Launcher() {

    /** Lazy, weil Launcher3 die Touch-Controller schon während `setupViews()` anlegt. */
    val jiggleMode by lazy { JiggleMode(this) }

    private val syncAllApps = Runnable { AllAppsOnHomeScreen.sync(this) }
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
        DockBackground(hotseat).attach()
        showPageDots()
        keepPageDotsAboveDock()
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
        // Home-Taste beendet den Bearbeitungsmodus
        if (Intent.ACTION_MAIN == intent.action) jiggleMode.exit()
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
     * Launcher3 setzt die Seitenpunkte direkt auf das Dock ([com.android.launcher3.Workspace]
     * `setPageIndicatorInset`). Wegen der Dock-Kapsel brauchen sie mehr Luft; der Abstand wird nach
     * jedem Neusetzen wieder ergänzt.
     */
    private fun keepPageDotsAboveDock() {
        val indicator = workspace.pageIndicator as View
        val extra = (PAGE_DOTS_EXTRA_SPACING_DP * resources.displayMetrics.density).toInt()
        indicator.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val lp = v.layoutParams as ViewGroup.MarginLayoutParams
            val wanted = deviceProfile.hotseatProfile.barSizePx + extra
            if (lp.bottomMargin != wanted) {
                lp.bottomMargin = wanted
                v.post { v.layoutParams = lp }
            }
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
        arrayOf(dragController, JiggleTouchController(this, jiggleMode))

    /** Wie Launcher3, aber „Entfernen“ fragt bei Apps nach Löschen oder Vom-Homescreen-Entfernen. */
    override fun getSupportedShortcuts(itemInfo: ItemInfo): Stream<SystemShortcut.Factory<*>> {
        val inHome = itemInfo.container == CONTAINER_DESKTOP || itemInfo.container == CONTAINER_HOTSEAT
        if (!inHome || itemInfo.itemType != ITEM_TYPE_APPLICATION) {
            return super.getSupportedShortcuts(itemInfo)
        }
        return Stream.of(APP_INFO, WIDGETS, INSTALL, EditHomeShortcut.FACTORY, RemoveAppShortcut.FACTORY)
    }

    override fun removeItem(
        v: View?,
        itemInfo: ItemInfo,
        deleteFromDb: Boolean,
        reason: String?,
    ): Boolean {
        if (deleteFromDb) HomeScreenRemovals.get(this).onRemovedFromHome(itemInfo)
        return super.removeItem(v, itemInfo, deleteFromDb, reason)
    }

    /** Popup-Controller, der nichts anzeigt (Bearbeitungsmodus). */
    private object NoPopup : PopupController<Launcher> {
        override fun show(view: View): Popup? = null

        override fun dismiss() {}
    }

    private companion object {
        const val SYNC_DELAY_MS = 300L
        const val PAGE_DOTS_EXTRA_SPACING_DP = 10f
    }
}
