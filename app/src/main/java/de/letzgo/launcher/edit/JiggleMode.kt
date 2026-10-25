package de.letzgo.launcher.edit

import android.appwidget.AppWidgetHostView
import android.graphics.Rect
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.WorkspaceLongPressOptions
import com.android.launcher3.popup.PopupCategory
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.popup.PopupData
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Bearbeitungsmodus wie auf iOS („Wackeln“): Icons, Ordner und Widgets wackeln und tragen ein
 * Minus-Badge, oben erscheinen „Bearbeiten“ und „Fertig“. Gesteuert über [JiggleTouchController];
 * die Launcher-Zustände bleiben unverändert (NORMAL, beim Ziehen SPRING_LOADED).
 */
class JiggleMode(private val launcher: Launcher) : Choreographer.FrameCallback {

    var isActive = false
        private set

    /** Set while our own widget picker is in front: leaving the launcher keeps edit mode. */
    var keepWhileAway = false

    private val density = launcher.resources.displayMetrics.density
    private val badges = WeakHashMap<View, MinusBadge>()
    // View, in deren Overlay das Badge liegt (bei Widgets der Container, siehe [badgeHost])
    private val badgeHosts = WeakHashMap<View, View>()
    private val handles = WeakHashMap<View, ResizeHandle>()
    private val tmpRect = Rect()
    private val tmpPoint = FloatArray(2)
    private var startNanos = 0L
    private val backCallback = OnBackInvokedCallback { exit() }
    private val editBar = EditBar(launcher, onEdit = ::showEditMenu, onDone = ::exit)
    private val pageOverview by lazy { PageOverview(launcher, onClosed = { enter() }, onAddPage = ::showNewPage) }

    /**
     * Neue Seite: Launcher3 hält leere Seiten nur als leere Zusatzseite am Ende (im Bearbeitungsmodus).
     * Dorthin blättern; sobald etwas darauf liegt, bleibt sie.
     */
    private fun showNewPage() {
        // Nach dem Ausblenden der Übersicht, sonst bleibt der Homescreen stehen
        launcher.workspace.postDelayed({
            enter()
            launcher.workspace.addExtraEmptyScreens()
            launcher.workspace.post { launcher.workspace.snapToPage(launcher.workspace.pageCount - 1) }
        }, NEW_PAGE_DELAY_MS)
    }

    fun enter() {
        if (isActive) return
        isActive = true
        startNanos = System.nanoTime()
        launcher.workspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        editBar.show()
        // Wie auf iOS ersetzen „Bearbeiten“ und „Fertig“ die Statusleiste (ihr Platz bleibt reserviert,
        // BuildConfig.STABLE_STATUS_BAR_INSET)
        launcher.window?.insetsController?.hide(WindowInsets.Type.statusBars())
        // Wie auf iOS: im Bearbeitungsmodus gibt es eine leere Seite am Ende, damit Seitenpunkte und
        // Seitenübersicht auch bei nur einer Seite erreichbar sind
        launcher.workspace.addExtraEmptyScreens()
        launcher.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            backCallback,
        )
        Choreographer.getInstance().postFrameCallback(this)
        launcher.updateDisallowBack()
    }

    fun exit() {
        if (!isActive) return
        isActive = false
        Choreographer.getInstance().removeFrameCallback(this)
        launcher.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        editBar.hide()
        launcher.window?.insetsController?.show(WindowInsets.Type.statusBars())
        // Während eines Ziehens räumt onDragEnd (LetzgoLauncher) die leere Seite weg
        if (!launcher.dragController.isDragging) launcher.workspace.removeExtraEmptyScreen(true)
        for ((view, badge) in badges) (badgeHosts[view] ?: view).overlay.remove(badge)
        for ((view, handle) in handles) (badgeHosts[view] ?: view).overlay.remove(handle)
        badges.clear()
        handles.clear()
        badgeHosts.clear()
        forEachItemView { it.rotation = 0f }
        launcher.updateDisallowBack()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isActive) return
        val seconds = (frameTimeNanos - startNanos) / 1e9
        removeStaleBadges()
        forEachItemView { view ->
            // Leicht unterschiedliche Frequenz und Phase je Element, damit nichts synchron wackelt
            val seed = System.identityHashCode(view)
            val period = PERIOD_S * (1 + (seed % 7) / 40.0)
            val phase = (seed % 360) * PI / 180
            view.rotation = (AMPLITUDE_DEG * sin(2 * PI * seconds / period + phase)).toFloat()
            updateBadge(view)
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    /** Badges und Griffe entfernter Elemente (Widgets liegen im Overlay des Containers) wegräumen. */
    private fun removeStaleBadges() {
        val gone = badges.keys.filter { it.parent == null || it.visibility != View.VISIBLE }
        for (view in gone) {
            val host = badgeHosts.remove(view) ?: view
            badges.remove(view)?.let { host.overlay.remove(it) }
            handles.remove(view)?.let { host.overlay.remove(it) }
        }
    }

    /** Element-View unter dem Punkt (DragLayer-Koordinaten) auf der aktuellen Seite oder im Dock. */
    fun itemViewAt(x: Float, y: Float): View? = visibleItemViews().firstOrNull { view ->
        launcher.dragLayer.getDescendantRectRelativeToSelf(view, tmpRect)
        tmpRect.contains(x.toInt(), y.toInt())
    }

    /** Element, dessen Minus-Badge unter dem Punkt liegt (mit etwas größerer Trefferfläche). */
    fun badgeViewAt(x: Float, y: Float): View? = visibleItemViews().firstOrNull { view ->
        val badge = badges[view] ?: return@firstOrNull false
        tmpPoint[0] = badge.bounds.exactCenterX()
        tmpPoint[1] = badge.bounds.exactCenterY()
        val scale = launcher.dragLayer.getDescendantCoordRelativeToSelf(badgeHosts[view] ?: view, tmpPoint)
        hypot(x - tmpPoint[0], y - tmpPoint[1]) <= BADGE_TOUCH_DP * density * scale
    }

    fun onBadgeTapped(view: View) {
        val info = view.tag as? ItemInfo ?: return
        RemoveItem.confirm(launcher, info, view)
    }

    fun isOnEditBar(x: Float, y: Float) = editBar.isOnButton(x, y)

    /** Liegt der Punkt (DragLayer-Koordinaten) auf den Seitenpunkten über dem Dock? */
    fun isOnPageDots(x: Float, y: Float): Boolean {
        val dots = launcher.workspace.pageIndicator as View
        if (dots.visibility != View.VISIBLE || dots.alpha == 0f) return false
        launcher.dragLayer.getDescendantRectRelativeToSelf(dots, tmpRect)
        return tmpRect.contains(x.toInt(), y.toInt())
    }

    val isPageOverviewOpen
        get() = overviewCreated && pageOverview.isOpen

    private var overviewCreated = false

    /** Seitenübersicht wie auf iOS; danach geht es im Bearbeitungsmodus weiter. */
    fun openPageOverview() {
        exit()
        launcher.workspace.removeExtraEmptyScreen(true)
        overviewCreated = true
        pageOverview.open()
        launcher.updateDisallowBack()
    }

    private fun showEditMenu(anchor: View) {
        val pos = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(anchor, pos)
        PopupContainer.showForMenuItems(
            launcher,
            launcher.dragLayer,
            WorkspaceLongPressOptions.getAll(launcher).map { item ->
                // Widgets: edit mode (and a new, empty page) stays while the widget picker is open
                if (item.labelResId != R.string.widget_button_text) item
                else item.copy { target, info, view ->
                    keepWhileAway = true
                    DefaultPage.holdCurrentPage = true
                    item.popupAction(target, info, view)
                }
            } +
                PopupData(R.drawable.ic_view_carousel, R.string.edit_pages, PopupCategory.SYSTEM_SHORTCUT) { _, _, _ ->
                    AbstractFloatingView.closeAllOpenViews(launcher)
                    openPageOverview()
                },
            pos,
        )
    }

    private fun updateBadge(view: View) {
        val host = badgeHost(view)
        val badge =
            badges.getOrPut(view) {
                badgeHosts[view] = host
                MinusBadge(launcher, BADGE_DIAMETER_DP * density).also { host.overlay.add(it) }
            }
        when (view) {
            is BubbleTextView -> view.getIconBounds(tmpRect)
            is FolderIcon -> view.getPreviewBounds(tmpRect)
            // Widgets: ohne ihren Innenabstand (WIDGETS_ALIGN_TO_ICONS), also an der sichtbaren Kante
            else -> tmpRect.set(view.paddingLeft, view.paddingTop, view.width - view.paddingRight, view.height - view.paddingBottom)
        }
        if (host !== view) tmpRect.offset((view.left + view.translationX).toInt(), (view.top + view.translationY).toInt())
        val inset = BADGE_DIAMETER_DP * density * 0.15f
        badge.setCenter(tmpRect.left + inset, tmpRect.top + inset)
        if (view is AppWidgetHostView) {
            val handle =
                handles.getOrPut(view) {
                    ResizeHandle(launcher, HANDLE_DP * density).also { host.overlay.add(it) }
                }
            handle.setCorner(tmpRect.right.toFloat(), tmpRect.bottom.toFloat())
        }
    }

    /** Widget, dessen Eckgriff unter dem Punkt (DragLayer-Koordinaten) liegt. */
    fun handleViewAt(x: Float, y: Float): View? = visibleItemViews().firstOrNull { view ->
        val handle = handles[view] ?: return@firstOrNull false
        tmpPoint[0] = handle.bounds.exactCenterX()
        tmpPoint[1] = handle.bounds.exactCenterY()
        val scale = launcher.dragLayer.getDescendantCoordRelativeToSelf(badgeHosts[view] ?: view, tmpPoint)
        hypot(x - tmpPoint[0], y - tmpPoint[1]) <= HANDLE_DP * density * scale
    }

    /** Eckgriff angetippt: Launcher3-Rahmen zum Ziehen der Größe. */
    fun onHandleTapped(view: View) {
        val widget = view as? LauncherAppWidgetHostView ?: return
        val cellLayout = widget.parent?.parent as? CellLayout ?: return
        AppWidgetResizeFrame.showForWidget(widget, cellLayout)
    }

    /**
     * Widgets beschneiden sich auf ihre abgerundete Form; ein Badge in ihrer Ecke wäre unsichtbar.
     * Es liegt deshalb im Overlay des Containers darüber.
     */
    private fun badgeHost(view: View): View =
        if (view is AppWidgetHostView) view.parent as? View ?: view else view

    private fun visibleItemViews(): List<View> {
        val pages = listOfNotNull(launcher.workspace.getChildAt(launcher.workspace.currentPage))
        return (pages + launcher.hotseat).flatMap { itemViewsOf(it as CellLayout) }
    }

    private inline fun forEachItemView(action: (View) -> Unit) {
        val workspace = launcher.workspace
        for (i in 0 until workspace.childCount) {
            (workspace.getChildAt(i) as? CellLayout)?.let { itemViewsOf(it).forEach(action) }
        }
        itemViewsOf(launcher.hotseat).forEach(action)
    }

    private fun itemViewsOf(layout: CellLayout): List<View> {
        val container = layout.shortcutsAndWidgets
        return (0 until container.childCount)
            .map(container::getChildAt)
            .filter { it.tag is ItemInfo && it.visibility == View.VISIBLE }
    }

    private companion object {
        const val AMPLITUDE_DEG = 1.6
        const val PERIOD_S = 0.28
        const val BADGE_DIAMETER_DP = 22f
        const val BADGE_TOUCH_DP = 22f
        // Etwa der Eckenradius der Widgets
        const val HANDLE_DP = 20f
        const val NEW_PAGE_DELAY_MS = 250L
    }
}
