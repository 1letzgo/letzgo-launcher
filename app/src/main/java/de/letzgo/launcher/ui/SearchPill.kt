package de.letzgo.launcher.ui

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.android.launcher3.Launcher
import com.android.launcher3.R

/**
 * „Suchen“-Pille über dem Dock (iOS 26). Sitzt an der Stelle der Seitenpunkte: im Ruhezustand ist
 * die Pille zu sehen, während die Seiten scrollen die Punkte.
 */
class SearchPill(private val launcher: Launcher, onClick: () -> Unit) {

    private val density = launcher.resources.displayMetrics.density
    private val hideRunnable = Runnable { setScrolling(false) }
    private var enabled = true

    private val pill =
        TextView(launcher).apply {
            setText(R.string.search_pill)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            val icon = launcher.getDrawable(R.drawable.ic_letzgo_search)!!.mutate()
            val size = (13 * density).toInt()
            icon.setBounds(0, 0, size, size)
            setCompoundDrawablesRelative(icon, null, null, null)
            compoundDrawablePadding = (5 * density).toInt()
            val h = (14 * density).toInt()
            val v = (5 * density).toInt()
            setPadding(h, v, h, v)
            background =
                GradientDrawable().apply {
                    cornerRadius = 100 * density
                    setColor(0x40FFFFFF)
                }
            setOnClickListener { onClick() }
        }

    private var centerX = 0f
    private var centerY = 0f

    fun attach() {
        launcher.dragLayer.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        pill.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
        launcher.workspace.setOnScrollChangeListener { _, _, _, _, _ ->
            setScrolling(true)
            pill.removeCallbacks(hideRunnable)
            pill.postDelayed(hideRunnable, SHOW_DOTS_MS)
        }
    }

    /** Mittelpunkt in DragLayer-Koordinaten (die DragLayer beachtet keine Gravity). */
    fun placeAt(x: Float, y: Float) {
        centerX = x
        centerY = y
        place()
    }

    private fun place() {
        pill.x = centerX - pill.width / 2f
        pill.y = centerY - pill.height / 2f
    }

    /** Im Bearbeitungsmodus zeigt iOS die Seitenpunkte statt der Pille. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        setScrolling(false)
    }

    private fun setScrolling(scrolling: Boolean) {
        val visible = enabled && !scrolling
        pill.isClickable = visible
        pill.animate().alpha(if (visible) 1f else 0f).setDuration(FADE_MS).start()
    }

    private companion object {
        const val SHOW_DOTS_MS = 900L
        const val FADE_MS = 150L
    }
}
