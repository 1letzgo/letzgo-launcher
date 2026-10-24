package de.letzgo.launcher.panels

import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
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
    // Feder wie beim Blättern (M3: räumliche Bewegung ohne Überschwingen)
    private val spring =
        SpringAnimation(FloatValueHolder()).apply {
            spring = SpringForce().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY).setStiffness(STIFFNESS)
            setMinimumVisibleChange(0.001f)
            addUpdateListener { _, value, _ -> progress = value }
            addEndListener { _, canceled, value, _ ->
                if (!canceled) if (value <= 0f) finishClose() else onFullyOpen()
            }
        }
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

    /** Wischen nach unten auf der Seite (z. B. Suche öffnen); true = erledigt. */
    protected open fun onSwipeDown(): Boolean = false

    /** Zurück-Taste innerhalb der Seite, z. B. aus einer Unteransicht; true = erledigt. */
    protected open fun onBack(): Boolean = false

    /** Wird beim ersten Hereinziehen aufgerufen, z. B. um Inhalte zu aktualisieren. */
    protected open fun onOpening() {}

    protected open fun onClosed() {}

    /** Beginn des Hereinziehens durch [PanelSwipeController]. */
    fun startTracking() {
        attach()
        spring.cancel()
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
        // Die Feder übernimmt die Geschwindigkeit des Fingers (in Seitenbreiten pro Sekunde)
        animateTo(if (open) 1f else 0f, velocityTowardsOpen / width.coerceAtLeast(1))
    }

    fun open() {
        startTracking()
        animateTo(1f)
    }

    fun close(animate: Boolean) {
        if (!isOpen) return
        if (animate) {
            animateTo(0f)
        } else {
            spring.cancel()
            progress = 0f
            finishClose()
        }
    }

    private fun attach() {
        if (parent == null) {
            launcher.dragLayer.addView(this, LayoutParams(MATCH, MATCH))
        }
        bringToFront()
    }

    private fun animateTo(target: Float, velocity: Float = 0f) {
        spring.cancel()
        spring.setStartValue(progress)
        spring.setStartVelocity(velocity)
        spring.animateToFinalPosition(target)
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
                    spring.cancel()
                    velocityTracker = VelocityTracker.obtain()
                    downX = ev.x
                    return true
                }
                val dy = ev.y - downY
                if (dy > touchSlop * 2 && dy > abs(dx) * 1.5f && onSwipeDown()) {
                    // Rest der Geste nicht an die Inhalte weitergeben
                    downY = Float.MAX_VALUE
                    return false
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
        const val STIFFNESS = 500f
        const val FLING_VELOCITY_DP = 500f
        const val DOCK_SLIDE = 0.6f
    }
}
