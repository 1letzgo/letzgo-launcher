package de.letzgo.launcher.panels

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState.NORMAL
import com.android.launcher3.util.TouchController
import kotlin.math.abs

/**
 * Wischgesten auf dem Homescreen:
 * - über die erste Seite hinaus nach rechts: [left] (derzeit keine Seite)
 * - über die letzte Seite hinaus nach links: [right] (App-Mediathek)
 * - nach unten: [onSwipeDown] mit der x-Position des Fingers (Mitteilungen bzw. Schnelleinstellungen)
 */
class PanelSwipeController(
    private val launcher: Launcher,
    private val left: () -> SidePanel?,
    private val right: () -> SidePanel?,
    private val isBlocked: () -> Boolean,
    private val onSwipeDown: (x: Float) -> Unit,
) : TouchController {

    private val touchSlop = ViewConfiguration.get(launcher).scaledTouchSlop * 0.8f
    private var downX = 0f
    private var downY = 0f
    private var tracking: SidePanel? = null
    private var trackingFromLeft = false
    private var swipeDownHandled = false
    private var velocityTracker: VelocityTracker? = null

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                tracking = null
                swipeDownHandled = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!canStart()) return false
                val dx = ev.x - downX
                val dy = ev.y - downY
                val workspace = launcher.workspace
                if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                    // Zielseite statt aktueller Seite: auch während die Seite noch einrastet (z. B.
                    // kurz nach der Home-Taste) greift die Geste
                    val page = workspace.destinationPage
                    val panel =
                        when {
                            dx > 0 && page == 0 -> left()
                            dx < 0 && page == workspace.pageCount - 1 -> right()
                            else -> null
                        } ?: return false
                    tracking = panel
                    trackingFromLeft = dx > 0
                    downX = ev.x
                    velocityTracker = VelocityTracker.obtain()
                    panel.startTracking()
                    return true
                }
                if (dy > touchSlop * 2 && dy > abs(dx) * 1.5f && !swipeDownHandled) {
                    swipeDownHandled = true
                    onSwipeDown(downX)
                    return true
                }
            }
        }
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        val panel = tracking ?: return true
        velocityTracker?.addMovement(ev)
        val distance = if (trackingFromLeft) ev.x - downX else downX - ev.x
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> panel.track(distance)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val tracker = velocityTracker
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                tracker?.recycle()
                velocityTracker = null
                tracking = null
                panel.release(if (trackingFromLeft) vx else -vx)
            }
        }
        return true
    }

    private fun canStart() =
        !isBlocked() &&
            launcher.isInState(NORMAL) &&
            AbstractFloatingView.getTopOpenView(launcher) == null &&
            !launcher.dragController.isDragging
}
