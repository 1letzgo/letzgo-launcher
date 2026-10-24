package de.letzgo.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Buchstabenleiste am rechten Rand einer alphabetischen Liste (wie in der iOS-Mediathek): Tippen
 * oder Ziehen springt zum Buchstaben, mit leichtem Tick bei jedem Wechsel.
 */
class LetterIndex(context: Context, private val onLetter: (Char) -> Unit) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 11 * resources.displayMetrics.scaledDensity
            isFakeBoldText = true
            setShadowLayer(3f, 0f, 1f, 0x8A000000.toInt())
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
    private fun rowHeight() = minOf(MAX_ROW_DP * density, height.toFloat() / letters.size.coerceAtLeast(1))

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
