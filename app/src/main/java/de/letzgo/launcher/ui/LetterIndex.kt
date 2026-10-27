package de.letzgo.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

/**
 * Buchstabenleiste am rechten Rand einer alphabetischen Liste (wie in der iOS-Mediathek): Tippen
 * oder Ziehen springt zum Buchstaben, mit leichtem Tick bei jedem Wechsel.
 */
class LetterIndex(context: Context, private val onLetter: (Char) -> Unit) : View(context) {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            // Schriftrolle wie die übrigen Texte auf dem Hintergrundbild, nur gezeichnet
            val style = TextView(M3.context(context)).also { M3.text(it, M3.Type.LABEL_SMALL, null) }
            typeface = style.typeface
            textSize = style.textSize
            color = style.currentTextColor
            setShadowLayer(style.shadowRadius, style.shadowDx, style.shadowDy, style.shadowColor)
        }
    private var letters = emptyList<Char>()
    private var current: Char? = null

    fun setLetters(value: List<Char>) {
        letters = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (letters.isEmpty()) return
        val step = rowHeight()
        val top = (height - step * letters.size) / 2
        letters.forEachIndexed { i, letter ->
            val y = top + step * i + step / 2 - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(letter.toString(), width / 2f, y, paint)
        }
    }

    /** Zeilenhöhe: höchstens 18 dp, bei vielen Buchstaben kleiner, damit alle passen. */
    private fun rowHeight() = minOf(dpF(MAX_ROW_DP), height.toFloat() / letters.size.coerceAtLeast(1))

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (letters.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val step = rowHeight()
                val top = (height - step * letters.size) / 2
                val index = ((event.y - top) / step).toInt().coerceIn(0, letters.size - 1)
                val letter = letters[index]
                if (letter != current) {
                    current = letter
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onLetter(letter)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> current = null
        }
        return true
    }

    private companion object {
        const val MAX_ROW_DP = 18f
    }
}
