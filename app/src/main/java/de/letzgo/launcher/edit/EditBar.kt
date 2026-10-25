package de.letzgo.launcher.edit

import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.views.BaseDragLayer
import com.google.android.material.button.MaterialButton
import de.letzgo.launcher.ui.M3
import com.google.android.material.R as MaterialR

/**
 * Leiste oben im Bearbeitungsmodus mit Material-3-Buttons: links „Bearbeiten“ (Tonal, Menü mit
 * Widgets, Hintergrund, Einstellungen), rechts „Fertig“ (gefüllt).
 */
class EditBar(
    private val launcher: Launcher,
    onEdit: (View) -> Unit,
    onDone: () -> Unit,
) {
    private val m3 = M3.context(launcher)
    private val density = launcher.resources.displayMetrics.density
    private val edit =
        MaterialButton(m3).apply {
            setText(R.string.edit_bar_edit)
            backgroundTintList =
                ColorStateList.valueOf(M3.color(m3, MaterialR.attr.colorSecondaryContainer))
            setTextColor(M3.color(m3, MaterialR.attr.colorOnSecondaryContainer))
            setOnClickListener(onEdit)
        }
    private val done =
        MaterialButton(m3).apply {
            setText(R.string.edit_bar_done)
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
            // Wie auf iOS im Bereich der Statusleiste, damit die erste Icon-Reihe frei bleibt
            val lp =
                BaseDragLayer.LayoutParams(MATCH, launcher.deviceProfile.insets.top).apply {
                    gravity = Gravity.TOP
                    ignoreInsets = true
                }
            launcher.dragLayer.addView(bar, lp)
        }
        val side = (16 * density).toInt()
        // Etwas Abstand zum oberen Bildschirmrand (Kamera-Aussparung, abgerundete Ecken)
        val top = (TOP_PADDING_DP * density).toInt()
        bar.setPadding(side, top, side, 0)
        bar.layoutParams.height = launcher.deviceProfile.insets.top.coerceAtLeast((48 * density).toInt()) + top
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

    private companion object {
        const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
        const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        const val FADE_MS = 150L
        const val TOP_PADDING_DP = 12f
    }
}
