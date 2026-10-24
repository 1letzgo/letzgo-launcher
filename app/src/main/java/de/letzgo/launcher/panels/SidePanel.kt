package de.letzgo.launcher.panels

import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.animation.doOnEnd
import com.android.app.animation.Interpolators
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import de.letzgo.launcher.ui.M3
import kotlin.math.abs

/**
 * Eigene Homescreen-Seite links bzw. rechts der Seiten mit Apps (Heute-Ansicht, App-Mediathek).
 * Sie läuft beim Blättern über die erste bzw. letzte Seite hinaus mit dem Finger herein und schiebt
 * den Homescreen hinaus ([PanelSwipeController]); zurück per Wischen, Zurück oder Home.
 */
abstract class SidePanel(protected val launcher: Launcher, private val fromLeft: Boolean) :
    FrameLayout(launcher), Insettable {

    private val touchSlop = ViewConfiguration.get(launcher).scaledTouchSlop
    private val backCallback = OnBackInvokedCallback { if (!onBack()) close(animate = true) }
    private var animator: ValueAnimator? = null
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var closing = false

    /** 0 = ganz außerhalb, 1 = ganz offen. */
    var progress = 0f
        private set(value) {
            field = value.coerceIn(0f, 1f)
            applyProgress()
        }

    /** Sichtbar, auch während des Hereinziehens. */
    val isOpen
        get() = visibility == View.VISIBLE


    init {
        visibility = View.GONE
        isClickable = true
    }

    /**
     * Die Seite reicht hinter Status- und Gestenleiste; der Inhalt rückt oben ein, unten nur
     * scrollende Inhalte (siehe [onBottomInset]).
     */
    override fun setInsets(insets: Rect) {
        setPadding(insets.left, insets.top, insets.right, 0)
        onBottomInset(insets.bottom)
    }

    protected open fun onBottomInset(bottom: Int) {}

    /** Zurück-Taste innerhalb der Seite, z. B. aus einer Unteransicht; true = erledigt. */
    protected open fun onBack(): Boolean = false

    /** Wird beim ersten Hereinziehen aufgerufen, z. B. um Inhalte zu aktualisieren. */
    protected open fun onOpening() {}

    protected open fun onClosed() {}

    /** Beginn des Hereinziehens durch [PanelSwipeController]. */
    fun startTracking() {
        attach()
        animator?.cancel()
        if (progress == 0f) onOpening()
        visibility = View.VISIBLE
    }

    /** Verschiebung des Fingers seit Beginn in Pixeln (in Öffnungsrichtung positiv). */
    fun track(distance: Float) {
        progress = distance / width.coerceAtLeast(1)
    }

    /** Finger losgelassen: öffnen oder zurückfedern, je nach Strecke und Geschwindigkeit. */
    fun release(velocityTowardsOpen: Float) {
        val fling = FLING_VELOCITY_DP * resources.displayMetrics.density
        val open = velocityTowardsOpen > fling || (velocityTowardsOpen > -fling && progress > 0.4f)
        animateTo(if (open) 1f else 0f)
    }

    fun open() {
        startTracking()
        animateTo(1f)
    }

    fun close(animate: Boolean) {
        if (!isOpen) return
        if (animate) animateTo(0f) else progress = 0f
    }

    private fun attach() {
        if (parent == null) {
            launcher.dragLayer.addView(this, LayoutParams(MATCH, MATCH))
        }
        bringToFront()
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        animator =
            ValueAnimator.ofFloat(progress, target).apply {
                duration = (SETTLE_MS * abs(target - progress)).toLong().coerceAtLeast(120)
                interpolator = Interpolators.EMPHASIZED_DECELERATE
                addUpdateListener { progress = it.animatedValue as Float }
                doOnEnd { if (target == 0f) finishClose() else onFullyOpen() }
                start()
            }
    }

    private fun onFullyOpen() {
        launcher.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            backCallback,
        )
    }

    private fun finishClose() {
        visibility = View.GONE
        launcher.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        onClosed()
    }

    /**
     * Wie beim Blättern: Seite und Homescreen bewegen sich gemeinsam; Dock und Seitenpunkte gehören
     * nur zu den App-Seiten und blenden aus.
     */
    private fun applyProgress() {
        val w = width.toFloat()
        translationX = if (fromLeft) -w * (1 - progress) else w * (1 - progress)
        launcher.workspace.translationX = if (fromLeft) w * progress else -w * progress
        val hotseat = launcher.hotseat
        hotseat.alpha = 1 - progress
        hotseat.translationY = hotseat.height * DOCK_SLIDE * progress
        (launcher.workspace.pageIndicator as View).alpha = 1 - progress
        M3.setSystemBarsForSurface(launcher, onSurface = progress > 0.5f)
    }

    // Schließen per Wischen zurück zur Kante, wie sie hereinkam
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                closing = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val towardsEdge = if (fromLeft) -dx else dx
                if (towardsEdge > touchSlop && abs(dx) > abs(ev.y - downY) * 1.5f) {
                    closing = true
                    animator?.cancel()
                    velocityTracker = VelocityTracker.obtain()
                    downX = ev.x
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!closing) return super.onTouchEvent(ev)
        velocityTracker?.addMovement(ev)
        val towardsEdge = if (fromLeft) downX - ev.x else ev.x - downX
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> progress = 1 - towardsEdge / width.coerceAtLeast(1)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val tracker = velocityTracker
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                tracker?.recycle()
                velocityTracker = null
                closing = false
                release(if (fromLeft) vx else -vx)
            }
        }
        return true
    }

    private companion object {
        const val MATCH = LayoutParams.MATCH_PARENT
        const val SETTLE_MS = 380f
        const val FLING_VELOCITY_DP = 500f
        const val DOCK_SLIDE = 0.6f
    }
}
