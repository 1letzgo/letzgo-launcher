package de.letzgo.launcher

import com.android.launcher3.LauncherApplication
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.shapes.ShapesProvider

class LetzgoApplication : LauncherApplication() {

    override fun onCreate() {
        applyDefaults()
        super.onCreate()
    }

    /** Setzt iOS-artige Voreinstellungen, solange der Nutzer nichts anderes gewählt hat. */
    private fun applyDefaults() {
        val prefs = LauncherPrefs.get(this)
        if (prefs.get(ThemeManager.PREF_ICON_SHAPE).isEmpty()) {
            // Abgerundetes Quadrat mit weichen Ecken, kommt dem iOS-Squircle am nächsten
            prefs.put(ThemeManager.PREF_ICON_SHAPE, ShapesProvider.SQUARE_KEY)
        }
    }
}
