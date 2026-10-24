package de.letzgo.launcher.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewTreeObserver
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Hotseat
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.Launcher

/**
 * Halbtransparente Fläche hinter den Dock-Icons in der Farbe der Ordner-Kacheln, mit großen
 * M3-Ecken. Reicht fast bis an die Bildschirmränder und umschließt die Icons vertikal mit etwas Abstand.
 *
 * Eigene View in der DragLayer direkt unter dem [Hotseat], nicht dessen Hintergrund: Die Fläche ragt
 * über den Hotseat hinaus, und sobald er halbtransparent oder in einer eigenen Ebene gezeichnet wird
 * (beim Wechsel auf Heute-Seite oder Mediathek), schneidet Android an seinen Rändern ab. Position,
 * Verschiebung, Skalierung und Deckkraft übernimmt die View vor jedem Frame vom Hotseat.
 */
class DockBackground(private val launcher: Launcher) :
    View(launcher), View.OnLayoutChangeListener, ViewTreeObserver.OnPreDrawListener {

    private val hotseat: Hotseat = launcher.hotseat
    private val density = resources.displayMetrics.density
    private val fill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = M3.folderTileColor(launcher)
        }
    // Kapsel in Hotseat-Koordinaten
    private val capsule = RectF()
    private val iconBounds = Rect()
    private val location = IntArray(2)
    private val hotseatLocation = IntArray(2)
    private var lastState = 0

    /** Oberkante der Kapsel in Hotseat-Koordinaten; null, solange noch nichts gelayoutet ist. */
    val capsuleTop: Float?
        get() = capsule.top.takeUnless { capsule.isEmpty }

    /** Wird aufgerufen, wenn sich die Lage der Kapsel ändert (Seitenpunkte folgen). */
    var onCapsuleChanged: () -> Unit = {}

    fun attach() {
        val dragLayer = launcher.dragLayer
        val lp =
            InsettableFrameLayout.LayoutParams(
                    InsettableFrameLayout.LayoutParams.MATCH_PARENT,
                    InsettableFrameLayout.LayoutParams.MATCH_PARENT,
                )
                .apply { ignoreInsets = true }
        dragLayer.addView(this, dragLayer.indexOfChild(hotseat), lp)
        hotseat.shortcutsAndWidgets.addOnLayoutChangeListener(this)
        dragLayer.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onLayoutChange(
        v: View, left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
    ) {
        val icons = hotseat.shortcutsAndWidgets
        val icon = (0 until icons.childCount).map(icons::getChildAt).firstOrNull { it is BubbleTextView }
            as BubbleTextView? ?: return
        icon.getIconBounds(iconBounds)
        val iconTop = icons.top + icon.top + iconBounds.top
        val padding = PADDING_DP * density
        val margin = MARGIN_DP * density
        capsule.set(
            margin,
            iconTop - padding,
            hotseat.width - margin,
            iconTop + iconBounds.height() + padding,
        )
        invalidate()
        onCapsuleChanged()
    }

    /**
     * Übernimmt Sichtbarkeit und Deckkraft des Hotseats; zeichnet nur neu, wenn er sich bewegt oder
     * skaliert hat (sonst entstünde eine Endlosschleife von Frames).
     */
    override fun onPreDraw(): Boolean {
        val visible = hotseat.isShown && hotseat.alpha > 0f
        val newVisibility = if (visible) VISIBLE else INVISIBLE
        if (visibility != newVisibility) visibility = newVisibility
        if (alpha != hotseat.alpha) alpha = hotseat.alpha
        hotseat.getLocationInWindow(hotseatLocation)
        val state = (hotseatLocation[0] * 31 + hotseatLocation[1]) * 31 + hotseat.scaleX.hashCode()
        if (state != lastState) {
            lastState = state
            invalidate()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (capsule.isEmpty) return
        // Hotseat-Koordinaten in eigene umrechnen, inklusive Verschiebung und Skalierung
        getLocationInWindow(location)
        hotseat.getLocationInWindow(hotseatLocation)
        canvas.save()
        canvas.translate(
            (hotseatLocation[0] - location[0]).toFloat(),
            (hotseatLocation[1] - location[1]).toFloat(),
        )
        canvas.scale(hotseat.scaleX, hotseat.scaleY)
        val radius = M3.SHAPE_EXTRA_LARGE_DP * density
        canvas.drawRoundRect(capsule, radius, radius, fill)
        canvas.restore()
    }

    private companion object {
        const val MARGIN_DP = 12f
        const val PADDING_DP = 10f
    }
}
