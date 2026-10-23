package de.letzgo.launcher.edit

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
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
 * - Tippen auf eine freie Stelle beendet den Modus.
 */
class JiggleTouchController(private val launcher: Launcher, private val jiggle: JiggleMode) :
    TouchController {

    private val touchSlop = ViewConfiguration.get(launcher).scaledTouchSlop * DRAG_SLOP_FACTOR
    private var downX = 0f
    private var downY = 0f
    private var downView: View? = null
    private var badgeView: View? = null
    private var moved = false
    private var dragging = false
    private var ignore = false

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
                badgeView = jiggle.badgeViewAt(ev.x, ev.y)
                downView = badgeView ?: jiggle.itemViewAt(ev.x, ev.y)
                return badgeView != null
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
                // Tippen ins Leere beendet den Modus
                if (!ignore && !moved && downView == null) jiggle.exit()
            }
        }
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        if (dragging) return launcher.dragController.onControllerTouchEvent(ev)
        val badge = badgeView ?: return false
        if (ev.actionMasked == MotionEvent.ACTION_UP &&
            hypot(ev.x - downX, ev.y - downY) < touchSlop
        ) {
            jiggle.onBadgeTapped(badge)
        }
        return true
    }

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
