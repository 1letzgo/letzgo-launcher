package de.letzgo.launcher.edit

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import de.letzgo.launcher.ui.M3
import com.google.android.material.R as MaterialR

/** Knopf zum Entfernen an der oberen linken Ecke im Bearbeitungsmodus, in Material-3-Farben. */
class MinusBadge(context: Context, private val diameter: Float) : Drawable() {

    private val m3 = M3.context(context)
    private val circle =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = M3.color(m3, MaterialR.attr.colorSurfaceContainerHighest)
            // Leichter Schatten: sichtbar auch auf hellem Hintergrundbild
            setShadowLayer(diameter * 0.1f, 0f, diameter * 0.04f, M3.SHADOW)
        }
    private val minus =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = M3.color(m3, MaterialR.attr.colorOnSurface)
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
        circle.alpha = alpha
        minus.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        circle.colorFilter = colorFilter
        minus.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
