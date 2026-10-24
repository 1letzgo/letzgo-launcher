package de.letzgo.launcher.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Hotseat

/**
 * Glas-Kapsel hinter den Dock-Icons. Liegt als Hintergrund auf dem [Hotseat], reicht fast bis an
 * die Bildschirmränder und umschließt die Icons vertikal mit etwas Abstand. Echter Weichzeichner folgt mit dem Liquid-Glass-Material (M4).
 */
class DockBackground(private val hotseat: Hotseat) : Drawable(), View.OnLayoutChangeListener {

    private val density = Resources.getSystem().displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FILL_COLOR }
    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = STROKE_COLOR
        }
    private val capsule = RectF()

    /** Oberkante der Kapsel in Hotseat-Koordinaten; null, solange noch nichts gelayoutet ist. */
    val capsuleTop: Float?
        get() = capsule.top.takeUnless { capsule.isEmpty }

    /** Wird aufgerufen, wenn sich die Lage der Kapsel ändert (Seitenpunkte und Suchen-Pille folgen). */
    var onCapsuleChanged: () -> Unit = {}
    private val iconBounds = Rect()

    fun attach() {
        hotseat.background = this
        hotseat.shortcutsAndWidgets.addOnLayoutChangeListener(this)
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
        invalidateSelf()
        onCapsuleChanged()
    }

    override fun draw(canvas: Canvas) {
        if (capsule.isEmpty) return
        val radius = capsule.height() / 2.6f
        canvas.drawRoundRect(capsule, radius, radius, fill)
        canvas.drawRoundRect(capsule, radius, radius, stroke)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha * FILL_ALPHA / 255
        stroke.alpha = alpha * STROKE_ALPHA / 255
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        stroke.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val MARGIN_DP = 12f
        const val PADDING_DP = 10f
        const val FILL_ALPHA = 0x40
        const val STROKE_ALPHA = 0x4D
        const val FILL_COLOR = (FILL_ALPHA shl 24) or 0xFFFFFF
        const val STROKE_COLOR = (STROKE_ALPHA shl 24) or 0xFFFFFF
    }
}
