package de.letzgo.launcher.edit

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Graues Minus-Badge an der oberen linken Ecke im Bearbeitungsmodus, wie auf iOS. */
class MinusBadge(private val diameter: Float) : Drawable() {

    private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CIRCLE_COLOR }
    private val minus =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MINUS_COLOR
            strokeWidth = diameter * 0.12f
            strokeCap = Paint.Cap.ROUND
        }

    /** Setzt den Mittelpunkt in Koordinaten der View, in deren Overlay das Badge liegt. */
    fun setCenter(x: Float, y: Float) {
        val r = diameter / 2
        setBounds((x - r).toInt(), (y - r).toInt(), (x + r).toInt(), (y + r).toInt())
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = diameter / 2
        canvas.drawCircle(cx, cy, r, circle)
        canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, minus)
    }

    override fun setAlpha(alpha: Int) {
        circle.alpha = alpha * CIRCLE_ALPHA / 255
        minus.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        circle.colorFilter = colorFilter
        minus.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val CIRCLE_ALPHA = 0xE6
        const val CIRCLE_COLOR = (CIRCLE_ALPHA shl 24) or 0xE5E5EA
        const val MINUS_COLOR = 0xFF1C1C1E.toInt()
    }
}
