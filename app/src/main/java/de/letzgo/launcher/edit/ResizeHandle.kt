package de.letzgo.launcher.edit

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import de.letzgo.launcher.ui.M3
import com.google.android.material.R as MaterialR

/**
 * Griff an der unteren rechten Ecke eines Widgets im Bearbeitungsmodus (wie auf iOS): ein Bogen
 * entlang der abgerundeten Ecke, in Material-3-Farben.
 */
class ResizeHandle(context: Context, private val size: Float) : Drawable() {

    private val m3 = M3.context(context)
    private val track =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = size * 0.22f
            color = M3.color(m3, MaterialR.attr.colorSurfaceContainerHighest)
            // Schatten wie beim Minus-Badge, damit der Griff auf hellen Widgets sichtbar ist
            setShadowLayer(size * 0.12f, 0f, size * 0.04f, M3.SHADOW)
        }
    private val arc = RectF()

    /** Setzt die Ecke (unten rechts) in Koordinaten der View, in deren Overlay der Griff liegt. */
    fun setCorner(x: Float, y: Float) {
        setBounds((x - size).toInt(), (y - size).toInt(), x.toInt(), y.toInt())
    }

    /** Bogen außen entlang der abgerundeten Widget-Ecke (Radius [size]). */
    override fun draw(canvas: Canvas) {
        val outside = track.strokeWidth * 0.9f
        val cx = bounds.right - size
        val cy = bounds.bottom - size
        val r = size + outside
        arc.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(arc, 5f, 80f, false, track)
    }

    override fun setAlpha(alpha: Int) {
        track.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        track.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
