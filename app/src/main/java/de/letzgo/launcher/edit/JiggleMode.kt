package de.letzgo.launcher.edit

import android.graphics.Rect
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.WorkspaceLongPressOptions
import com.android.launcher3.popup.PopupContainer
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

    private val density = launcher.resources.displayMetrics.density
    private val badges = WeakHashMap<View, MinusBadge>()
    private val tmpRect = Rect()
    private val tmpPoint = FloatArray(2)
    private var startNanos = 0L
    private val backCallback = OnBackInvokedCallback { exit() }
    private val editBar = EditBar(launcher, onEdit = ::showEditMenu, onDone = ::exit)
    private val pageOverview by lazy { PageOverview(launcher) { enter() } }

    fun enter() {
        if (isActive) return
        isActive = true
        startNanos = System.nanoTime()
        launcher.workspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        editBar.show()
        launcher.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            backCallback,
        )
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun exit() {
        if (!isActive) return
        isActive = false
        Choreographer.getInstance().removeFrameCallback(this)
        launcher.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        editBar.hide()
        for ((view, badge) in badges) view.overlay.remove(badge)
        badges.clear()
        forEachItemView { it.rotation = 0f }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isActive) return
        val seconds = (frameTimeNanos - startNanos) / 1e9
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
        val scale = launcher.dragLayer.getDescendantCoordRelativeToSelf(view, tmpPoint)
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
        overviewCreated = true
        pageOverview.open()
    }

    private fun showEditMenu(anchor: View) {
        val pos = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(anchor, pos)
        PopupContainer.showForMenuItems(
            launcher,
            launcher.dragLayer,
            WorkspaceLongPressOptions.getAll(launcher),
            pos,
        )
    }

    private fun updateBadge(view: View) {
        val badge =
            badges.getOrPut(view) {
                MinusBadge(launcher, BADGE_DIAMETER_DP * density).also { view.overlay.add(it) }
            }
        when (view) {
            is BubbleTextView -> view.getIconBounds(tmpRect)
            is FolderIcon -> view.getPreviewBounds(tmpRect)
            else -> tmpRect.set(0, 0, view.width, view.height)
        }
        val inset = BADGE_DIAMETER_DP * density * 0.15f
        badge.setCenter(tmpRect.left + inset, tmpRect.top + inset)
    }

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
    }
}
