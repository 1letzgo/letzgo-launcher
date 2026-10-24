package de.letzgo.launcher

import com.android.launcher3.LauncherApplication
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.shapes.ShapesProvider

class LetzgoApplication : LauncherApplication() {

    override fun onCreate() {
        migrate()
        super.onCreate()
    }

    /**
     * Bis 0.5.0 hat letzgo die Icon-Form fest auf das abgerundete Quadrat gesetzt (iOS-Optik). Das
     * Design folgt jetzt Material 3, also gilt wieder die Form des Systems; einmalig zurücksetzen.
     */
    private fun migrate() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SHAPE_RESET, false)) return
        val launcherPrefs = LauncherPrefs.get(this)
        if (launcherPrefs.get(ThemeManager.PREF_ICON_SHAPE) == ShapesProvider.SQUARE_KEY) {
            launcherPrefs.put(ThemeManager.PREF_ICON_SHAPE, "")
        }
        prefs.edit().putBoolean(KEY_SHAPE_RESET, true).apply()
    }

    private companion object {
        const val PREFS = "letzgo_migrations"
        const val KEY_SHAPE_RESET = "icon_shape_reset"
    }
}
