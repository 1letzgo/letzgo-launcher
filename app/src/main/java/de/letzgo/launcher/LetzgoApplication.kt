package de.letzgo.launcher

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.LauncherApplication
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.shapes.ShapesProvider
import com.android.launcher3.util.OnboardingPrefs

class LetzgoApplication : LauncherApplication() {

    override fun onCreate() {
        appContext = this
        migrate()
        // Launcher3 lässt das Dock hüpfen, bis man einmal in den App-Drawer gewischt hat. letzgo hat
        // keinen Drawer, der Hinweis gilt daher als gesehen (sonst springt das Dock bei jeder Rückkehr).
        LauncherPrefs.get(this).put(OnboardingPrefs.HOME_BOUNCE_SEEN, true)
        super.onCreate()
    }

    /** Einmalige Umstellungen beim Update, vor dem Start von Launcher3. */
    private fun migrate() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        resetHomePagesOnce(prefs)
        resetIconShapeOnce(prefs)
    }

    /**
     * Bis 0.5.0 hat letzgo die Icon-Form fest auf das abgerundete Quadrat gesetzt (iOS-Optik). Das
     * Design folgt jetzt Material 3, also gilt wieder die Form des Systems.
     */
    private fun resetIconShapeOnce(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_SHAPE_RESET, false)) return
        val launcherPrefs = LauncherPrefs.get(this)
        if (launcherPrefs.get(ThemeManager.PREF_ICON_SHAPE) == ShapesProvider.SQUARE_KEY) {
            launcherPrefs.put(ThemeManager.PREF_ICON_SHAPE, "")
        }
        prefs.edit().putBoolean(KEY_SHAPE_RESET, true).apply()
    }

    /**
     * Einmalig mit 0.7.0: Homescreen-Seiten leeren. Frühere Versionen haben beim ersten Start alle
     * Apps auf die Seiten gelegt; jetzt ist der Homescreen eine Auswahl, die Mediathek hat alle Apps.
     * Die Launcher-Datenbank wird gelöscht, Launcher3 legt sie wie bei einer Neuinstallation mit dem
     * Standard-Dock neu an. Die Liste bekannter Apps bleibt, damit nichts automatisch zurückkommt.
     */
    private fun resetHomePagesOnce(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_HOME_PAGES_RESET, false)) return
        databaseList().filter { it.startsWith("launcher") && it.endsWith(".db") }.forEach(::deleteDatabase)
        prefs.edit().putBoolean(KEY_HOME_PAGES_RESET, true).apply()
    }

    companion object {
        /** Für Stellen ohne eigenen Kontext (z. B. Launcher3-Menüdaten). */
        lateinit var appContext: Context
            private set

        private const val PREFS = "letzgo_migrations"
        private const val KEY_SHAPE_RESET = "icon_shape_reset"
        private const val KEY_HOME_PAGES_RESET = "home_pages_reset_0_7"
    }
}
