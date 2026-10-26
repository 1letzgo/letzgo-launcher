package de.letzgo.launcher.edit

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.widget.resize.AppWidgetResizeFrameCompose
import com.android.launcher3.LauncherState.NORMAL
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.util.TouchController
import kotlin.math.hypot

/**
 * Touch-Verhalten im Bearbeitungsmodus:
 * - Ziehen an einem Element hebt es sofort an (ohne langes Drücken).
 * - Tippen auf ein Minus-Badge entfernt das Element ([RemoveItem]).
 * - Ziehen am Eckgriff eines Widgets ändert sofort die Größe (Tippen öffnet nur den Rahmen).
 * - Tippen auf die Seitenpunkte öffnet die Seitenübersicht ([PageOverview]).
 * - Tippen auf eine freie Stelle beendet den Modus.
 */
class JiggleTouchController(private val launcher: Launcher, private val jiggle: JiggleMode) :
    TouchController {

    private val touchSlop = ViewConfiguration.get(launcher).scaledTouchSlop * DRAG_SLOP_FACTOR
    private var downX = 0f
    private var downY = 0f
    private var downView: View? = null
    private var badgeView: View? = null
    private var handleView: View? = null
    private var moved = false
    private var dragging = false
    private var ignore = false
    private var onDots = false

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!jiggle.isActive) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                moved = false
                dragging = false
                ignore =
                    AbstractFloatingView.getTopOpenView(launcher) != null ||
                        jiggle.isOnEditBar(ev.x, ev.y)
                if (ignore) return false
                onDots = jiggle.isOnPageDots(ev.x, ev.y)
                badgeView = jiggle.badgeViewAt(ev.x, ev.y)
                handleView = if (badgeView == null) jiggle.handleViewAt(ev.x, ev.y) else null
                downView = badgeView ?: handleView ?: jiggle.itemViewAt(ev.x, ev.y)
                return badgeView != null || handleView != null
            }
            MotionEvent.ACTION_MOVE -> {
                if (ignore || moved) return false
                if (hypot(ev.x - downX, ev.y - downY) < touchSlop) return false
                moved = true
                val view = downView ?: return false
                if (startDrag(view)) {
                    dragging = true
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                when {
                    ignore || moved || downView != null -> {}
                    onDots -> jiggle.openPageOverview()
                    // Tippen ins Leere beendet den Modus
                    // Taps on "Edit"/"Done" (checked again: the down may not have reached us)
                    jiggle.isOnEditBar(ev.x, ev.y) -> {}
                    else -> jiggle.exit()
                }
            }
        }
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        if (dragging) return launcher.dragController.onControllerTouchEvent(ev)
        handleView?.let { return resizeWithHandle(it, ev) }
        val target = badgeView ?: return false
        if (ev.actionMasked == MotionEvent.ACTION_UP &&
            hypot(ev.x - downX, ev.y - downY) < touchSlop
        ) {
            jiggle.onBadgeTapped(target)
        }
        return true
    }

    /**
     * Corner handle: dragging it resizes the widget right away (like iOS), from the bottom-right
     * corner of the Launcher3 resize frame that opens on touch down; a plain tap leaves the frame
     * open.
     */
    private fun resizeWithHandle(widget: View, ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            jiggle.onHandleTapped(widget)
            frameStarted = false
            return true
        }
        val frame = AbstractFloatingView.getOpenView<AbstractFloatingView>(
            launcher, AbstractFloatingView.TYPE_WIDGET_RESIZE_FRAME
        ) as? AppWidgetResizeFrameCompose ?: return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!frameStarted) {
                    if (hypot(ev.x - downX, ev.y - downY) < touchSlop || frame.width == 0) return true
                    frameStarted = true
                    frame.letzgoBeginCornerResize()
                    lastX = downX
                    lastY = downY
                }
                frame.letzgoDragBy(ev.x - lastX, ev.y - lastY)
                lastX = ev.x
                lastY = ev.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (frameStarted) frame.letzgoEndResize()
        }
        return true
    }

    private var lastX = 0f
    private var lastY = 0f

    private var frameStarted = false

    private fun startDrag(view: View): Boolean {
        val info = view.tag as? ItemInfo ?: return false
        if (!ItemLongClickListener.canStartDrag(launcher) || !launcher.isInState(NORMAL)) {
            return false
        }
        ItemLongClickListener.beginDrag(view, launcher, info, DragOptions())
        return launcher.dragController.isDragging
    }

    private companion object {
        // Etwas kleiner als der System-Slop, damit das Anheben vor dem Seitenwechsel greift
        const val DRAG_SLOP_FACTOR = 0.7f
    }
}
