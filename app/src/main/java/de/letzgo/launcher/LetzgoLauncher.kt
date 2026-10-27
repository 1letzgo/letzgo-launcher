package de.letzgo.launcher

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_ACTION_POPUP
import com.android.launcher3.CellLayout
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
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.TouchController
import de.letzgo.launcher.edit.DefaultPage
import de.letzgo.launcher.edit.JiggleMode
import de.letzgo.launcher.edit.JiggleTouchController
import de.letzgo.launcher.model.AppUsage
import de.letzgo.launcher.model.HomeScreenApps
import de.letzgo.launcher.panels.AppLibraryPanel
import de.letzgo.launcher.panels.PanelSwipeController
import de.letzgo.launcher.popup.EditHomeShortcut
import de.letzgo.launcher.popup.RemoveAppShortcut
import de.letzgo.launcher.ui.Backdrop
import de.letzgo.launcher.ui.DockBackground
import de.letzgo.launcher.ui.FolderTitle
import de.letzgo.launcher.ui.M3
import de.letzgo.launcher.ui.dpF
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
    private lateinit var dock: DockBackground
    private var homeDimmedForFolder = false
    val backdrop by lazy { Backdrop(this) }
    private val folderTitle by lazy { FolderTitle(this) { dimHomeForFolder(false) } }
    private var popupOpen = false
    private var popupAnchor: View? = null
    private var resumeEditUntil = 0L
    private val blurredViews = mutableListOf<View>()

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
        // Widget picker closed without adding: back to normal handling
        dragLayer.postDelayed({
            jiggleMode.keepWhileAway = false
            DefaultPage.holdCurrentPage = false
        }, KEEP_EDIT_RESET_MS)
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
                override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) {
                    blurAroundPopup(false)
                    jiggleMode.enter()
                    // Leere Seite am Ende bleibt, solange der Bearbeitungsmodus läuft
                    workspace.deferRemoveExtraEmptyScreen()
                }

                override fun onDragEnd() {
                    // Leere Zusatzseite nur, solange der Bearbeitungsmodus läuft
                    dragLayer.post {
                        if (jiggleMode.isActive) workspace.addExtraEmptyScreens()
                        else workspace.removeExtraEmptyScreen(true)
                    }
                }
            }
        )
    }

    /**
     * Hinter einem offenen Ordner tritt der Homescreen zurück, damit durch den halbtransparenten
     * Ordner nur das Hintergrundbild scheint und keine Icons oder Beschriftungen.
     */
    override fun onDragLayerHierarchyChanged() {
        super.onDragLayerHierarchyChanged()
        val folder = Folder.getOpen(this)
        folderTitle.bind(folder?.takeIf { it.isOpen })
        val popup = AbstractFloatingView.hasOpenView(this, TYPE_ACTION_POPUP)
        if (popup != popupOpen) {
            popupOpen = popup
            animateBackdrop(Backdrop.POPUP, popup)
            blurAroundPopup(popup)
        }
        dimHomeForFolder(folder?.isOpen == true)
    }

    /** Beim Schließen schon zu Beginn der Animation zurück ([FolderTitle] meldet das). */
    private fun dimHomeForFolder(dim: Boolean) {
        if (dim == homeDimmedForFolder) return
        homeDimmedForFolder = dim
        val alpha = if (dim) 0f else 1f
        listOf(workspace, hotseat, workspace.pageIndicator as View).forEach {
            it.animate().alpha(alpha).setDuration(FOLDER_FADE_MS).setInterpolator(M3.Motion.STANDARD).start()
        }
        animateBackdrop(Backdrop.FOLDER, dim)
    }

    private fun animateBackdrop(key: String, on: Boolean) {
        ValueAnimator.ofFloat(if (on) 0f else 1f, if (on) 1f else 0f).apply {
            duration = M3.Motion.MEDIUM2
            interpolator = M3.Motion.STANDARD
            addUpdateListener { backdrop.set(key, it.animatedValue as Float) }
            start()
        }
    }

    /**
     * Ordner öffnen wie auf iOS mittig auf dem Bildschirm; darüber steht ihr Name ([FolderTitle]),
     * beide zusammen zentriert. Gleiche Lage wie eine geöffnete Mediathek-Kategorie.
     */
    override fun updateOpenFolderPosition(inOutPosition: IntArray, bounds: Rect, width: Int, height: Int) {
        val title = FolderTitle.spaceAbove(this)
        inOutPosition[0] = (dragLayer.width - width) / 2
        inOutPosition[1] = ((dragLayer.height - height + title) / 2).coerceAtLeast(deviceProfile.insets.top + title)
    }

    /**
     * Launcher3 sperrt auf dem Homescreen die Zurück-Geste des Systems. In unseren eigenen Ebenen
     * (Mediathek, Heute-Seite, Bearbeitungsmodus, Seitenübersicht) muss sie wie Zurück wirken.
     */
    override fun shouldDisableBackGesture(): Boolean =
        super.shouldDisableBackGesture() &&
            !appLibrary.isOpen && !jiggleMode.isActive && !jiggleMode.isPageOverviewOpen

    private var shownDefaultPage = false

    /** Nach dem ersten Laden auf der Standardseite beginnen ([DefaultPage]). */
    override fun finishBindingItems(pagesBoundFirst: com.android.launcher3.util.IntSet?) {
        super.finishBindingItems(pagesBoundFirst)
        if (!shownDefaultPage) {
            shownDefaultPage = true
            val page = DefaultPage.index(workspace)
            if (page != workspace.currentPage) workspace.setCurrentPage(page)
        }
    }

    /** Starts merken für die Vorschläge der Mediathek ([AppUsage]). */
    override fun startActivitySafely(v: View?, intent: Intent, item: ItemInfo?): RunnableList? {
        (item?.targetComponent ?: intent.component)?.let { AppUsage.record(this, it) }
        return super.startActivitySafely(v, intent, item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Back from widget binding/setup started in edit mode: continue editing (resize, move)
        if (System.currentTimeMillis() < resumeEditUntil) {
            resumeEditUntil = 0L
            dragLayer.post { jiggleMode.enter() }
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
        if (jiggleMode.isActive) NoPopup else Tracking(super.getPopupControllerForAppIcons())

    override fun getPopupControllerForHomeScreenItems(): PopupController<Launcher> =
        if (jiggleMode.isActive) NoPopup else Tracking(super.getPopupControllerForHomeScreenItems())

    /** Merkt sich das Element, zu dem ein Kontextmenü gehört (bleibt scharf, [blurAroundPopup]). */
    private inner class Tracking(private val inner: PopupController<Launcher>) : PopupController<Launcher> {
        override fun show(view: View): Popup? {
            popupAnchor = view
            return inner.show(view)
        }

        override fun dismiss() = inner.dismiss()
    }

    /**
     * Wie auf iOS: Bei offenem Kontextmenü werden die übrigen Elemente der Seite und des Docks
     * unscharf, das gedrückte bleibt scharf.
     */
    private fun blurAroundPopup(on: Boolean) {
        // Immer genau die Views zurücksetzen, die unscharf gemacht wurden (auch wenn inzwischen die
        // Seite gewechselt, ein Ziehen begonnen oder der Homescreen neu geladen wurde)
        blurredViews.forEach { it.setRenderEffect(null) }
        blurredViews.clear()
        if (!on) {
            popupAnchor = null
            return
        }
        val radius = dpF(POPUP_BLUR_DP)
        val effect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        val page = workspace.getChildAt(workspace.currentPage) as? CellLayout
        listOfNotNull(page, hotseat).forEach { layout ->
            val container = layout.shortcutsAndWidgets
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child === popupAnchor) continue
                child.setRenderEffect(effect)
                blurredViews += child
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home-Taste beendet den Bearbeitungsmodus und schließt die Mediathek
        if (Intent.ACTION_MAIN == intent.action) {
            // Coming back from our widget picker (it reopens home to add the widget): stay in edit
            // mode on the same page
            if (jiggleMode.keepWhileAway) {
                jiggleMode.keepWhileAway = false
                return
            }
            jiggleMode.exit()
            closeOverlays(animate = true)
        }
    }

    override fun onStop() {
        // A widget added from edit mode may still need the bind permission or its setup screen:
        // come back to edit mode afterwards (onActivityResult)
        if (jiggleMode.isActive) resumeEditUntil = System.currentTimeMillis() + RESUME_EDIT_MS
        if (!jiggleMode.keepWhileAway) jiggleMode.exit()
        blurAroundPopup(false)
        super.onStop()
    }

    override fun onStateSetStart(state: LauncherState) {
        super.onStateSetStart(state)
        showPageDots()
    }

    /** Seitenpunkte über dem Dock bleiben sichtbar und sind immer weiß (Launcher3 blendet sie aus). */
    private fun showPageDots() {
        workspace.pageIndicator.setShouldAutoHide(false)
        workspace.pageIndicator.setPaintColor(M3.ON_WALLPAPER)
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

    /** Offene Mediathek oder Heute-Seite schließen, z. B. bei der Home-Taste. */
    private fun closeOverlays(animate: Boolean) {
        appLibrary.close(animate)
    }

    /**
     * Seitenpunkte liegen mittig über der Dock-Kapsel. Launcher3 setzt die Punkte
     * direkt aufs Dock ([com.android.launcher3.Workspace] `setPageIndicatorInset`), deshalb wird
     * ihr Abstand nach jedem Neusetzen korrigiert.
     */
    private fun layoutDockAccessories() {
        val capsuleTop = dock.capsuleTop ?: return
        val indicator = workspace.pageIndicator as View
        val centerY = hotseat.top + capsuleTop - dpF(DOCK_ACCESSORY_GAP_DP)
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
                left = { null },
                right = { appLibrary },
                isBlocked = {
                    jiggleMode.isActive || jiggleMode.isPageOverviewOpen ||
                        appLibrary.isOpen
                },
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
        const val FOLDER_FADE_MS = M3.Motion.SHORT3
        const val POPUP_BLUR_DP = 6f
        const val KEEP_EDIT_RESET_MS = 1000L
        const val RESUME_EDIT_MS = 120_000L
    }
}
