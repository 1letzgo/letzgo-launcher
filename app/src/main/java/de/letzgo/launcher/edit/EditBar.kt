package de.letzgo.launcher.edit

import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.android.launcher3.Launcher
import com.android.launcher3.R

/**
 * Leiste oben im Bearbeitungsmodus: links „Bearbeiten“ (Menü mit Widgets, Hintergrund,
 * Einstellungen), rechts „Fertig“.
 */
class EditBar(
    private val launcher: Launcher,
    onEdit: (View) -> Unit,
    onDone: () -> Unit,
) {
    private val density = launcher.resources.displayMetrics.density
    private val edit = pill(R.string.edit_bar_edit).apply { setOnClickListener(onEdit) }
    private val done =
        pill(R.string.edit_bar_done).apply {
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { onDone() }
        }
    private val bar =
        FrameLayout(launcher).apply {
            addView(edit, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL))
            addView(done, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL))
            alpha = 0f
            visibility = View.GONE
        }

    fun show() {
        if (bar.parent == null) {
            launcher.dragLayer.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        }
        // Die DragLayer rückt ihre Kinder selbst unter die Statusleiste
        val side = (16 * density).toInt()
        bar.setPadding(side, (4 * density).toInt(), side, 0)
        bar.bringToFront()
        bar.visibility = View.VISIBLE
        bar.animate().alpha(1f).setDuration(FADE_MS).withEndAction(null).start()
    }

    fun hide() {
        bar.animate().alpha(0f).setDuration(FADE_MS).withEndAction { bar.visibility = View.GONE }.start()
    }

    /** Liegt der Punkt (DragLayer-Koordinaten) auf einem Knopf der Leiste? */
    fun isOnButton(x: Float, y: Float): Boolean {
        if (bar.visibility != View.VISIBLE) return false
        return listOf(edit, done).any { button ->
            val rect = Rect()
            launcher.dragLayer.getDescendantRectRelativeToSelf(button, rect)
            rect.contains(x.toInt(), y.toInt())
        }
    }

    private fun pill(text: Int) =
        TextView(launcher).apply {
            setText(text)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            val h = (16 * density).toInt()
            val v = (6 * density).toInt()
            setPadding(h, v, h, v)
            background =
                GradientDrawable().apply {
                    cornerRadius = 100 * density
                    // Dunkles Glas: lesbar auf hellen wie dunklen Hintergrundbildern
                    setColor(0x59000000)
                    setStroke(density.toInt().coerceAtLeast(1), 0x40FFFFFF)
                }
        }

    private companion object {
        const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
        const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        const val FADE_MS = 150L
    }
}
