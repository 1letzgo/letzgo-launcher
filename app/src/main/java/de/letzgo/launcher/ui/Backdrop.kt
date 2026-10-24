package de.letzgo.launcher.ui

import android.graphics.Color
import android.view.View
import android.view.WindowManager

import com.android.launcher3.Launcher
import com.android.launcher3.views.BaseDragLayer

/**
 * Hintergrund hinter geöffneten Ebenen (Ordner, Mediathek, Heute-Seite, Kontextmenüs): das
 * Hintergrundbild wird wie auf iOS unscharf und leicht abgedunkelt, damit Inhalte darauf lesbar
 * bleiben. Unschärfe nur, wo das Gerät fensterübergreifende Unschärfe kann; sonst bleibt die
 * Abdunklung (M3-Scrim).
 *
 * Jede Ebene meldet ihren Anteil (0..1) unter eigenem Schlüssel; es gilt der größte.
 */
class Backdrop(private val launcher: Launcher) {

    private val levels = mutableMapOf<String, Float>()
    private var applied = -1f
    private val density = launcher.resources.displayMetrics.density
    private val scrim by lazy {
        View(launcher).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Ganz unten in der DragLayer, also unter Homescreen, Dock und allen Ebenen
            launcher.dragLayer.addView(this, 0, BaseDragLayer.LayoutParams(-1, -1).apply { ignoreInsets = true })
        }
    }

    fun set(key: String, level: Float) {
        levels[key] = level.coerceIn(0f, 1f)
        apply()
    }

    private fun apply() {
        val level = levels.values.maxOrNull() ?: 0f
        if (level == applied) return
        applied = level
        scrim.alpha = level * SCRIM_ALPHA
        scrim.visibility = if (level > 0f) View.VISIBLE else View.GONE
        val windowManager = launcher.getSystemService(WindowManager::class.java)
        if (windowManager.isCrossWindowBlurEnabled) {
            launcher.window?.setBackgroundBlurRadius((level * BLUR_DP * density).toInt())
        }
    }

    companion object {
        const val FOLDER = "folder"
        const val POPUP = "popup"
        const val LIBRARY = "library"
        const val TODAY = "today"
        const val OVERVIEW = "overview"

        // M3-Scrim ist 32 % Schwarz; mit Unschärfe reicht weniger
        private const val SCRIM_ALPHA = 0.24f
        private const val BLUR_DP = 24f
    }
}
