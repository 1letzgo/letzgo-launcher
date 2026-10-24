package de.letzgo.launcher.settings

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.GRID_NAME
import com.android.launcher3.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.letzgo.launcher.ui.M3

/**
 * Raster und Icon-Größe des Homescreens. Die Raster stehen in `res/xml/device_profiles.xml`;
 * Launcher3 zieht beim Wechsel die Elemente ins neue Raster um. Die Icon-Größe ist ein Faktor auf
 * die Größen des Rasters ([InvariantDeviceProfile], BuildConfig.ICON_SIZE_SETTING).
 */
object HomeLayoutSettings {

    private const val PREFS = "letzgo_settings"
    private const val KEY_ICON_SCALE = "icon_scale_percent"
    private const val KEY_GRID = "letzgo_grid"
    private const val DEFAULT_GRID = "ios_4_by_6"
    private const val MIN_PERCENT = 80
    private const val MAX_PERCENT = 120
    private const val DEFAULT_PERCENT = 100
    private const val STEP = 5

    /** Raster: Name in device_profiles.xml zu Anzeige (Spalten × Reihen). */
    private val GRIDS = listOf("ios_4_by_5" to "4 × 5", "ios_4_by_6" to "4 × 6", "ios_5_by_6" to "5 × 6", "ios_5_by_7" to "5 × 7")

    @JvmStatic
    fun iconScale(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ICON_SCALE, DEFAULT_PERCENT)
            .coerceIn(MIN_PERCENT, MAX_PERCENT) / 100f

    /**
     * Launcher3 hält für jedes Raster eine eigene Datenbank und übernimmt beim Wechsel nur Elemente,
     * die dort noch fehlen; schon vorhandene behalten ihre alte Lage und Größe. Wie auf iOS soll der
     * aktuelle Homescreen umziehen: alter Stand des Zielrasters wird vorher gelöscht.
     */
    private fun switchGrid(context: Context, idp: InvariantDeviceProfile, grid: String) {
        if (grid == (LauncherPrefs.get(context).get(GRID_NAME).takeUnless { it.isNullOrEmpty() } ?: DEFAULT_GRID)) return
        context.deleteDatabase("launcher_$grid.db")
        idp.setCurrentGrid(grid)
    }

    /** Fügt „Raster“ und „Icon-Größe“ oben in die Einstellungen ein. */
    fun addTo(screen: PreferenceScreen) {
        val context = screen.context
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val current = LauncherPrefs.get(context).get(GRID_NAME).takeUnless { it.isNullOrEmpty() } ?: DEFAULT_GRID
        // Eigener M3-Dialog: der Auswahldialog von ListPreference braucht ein AppCompat-Theme,
        // das die Einstellungsseite von Launcher3 nicht hat
        screen.addPreference(
            Preference(context).apply {
                key = KEY_GRID
                title = context.getString(R.string.settings_grid)
                summary = GRIDS.firstOrNull { it.first == current }?.second
                isPersistent = false
                order = -20
                isIconSpaceReserved = false
                setOnPreferenceClickListener { pref ->
                    val selected = GRIDS.indexOfFirst { it.first == (LauncherPrefs.get(context).get(GRID_NAME).takeUnless { n -> n.isNullOrEmpty() } ?: DEFAULT_GRID) }
                    MaterialAlertDialogBuilder(M3.context(context))
                        .setTitle(R.string.settings_grid)
                        .setSingleChoiceItems(GRIDS.map { it.second }.toTypedArray(), selected) { dialog, which ->
                            switchGrid(context, idp, GRIDS[which].first)
                            pref.summary = GRIDS[which].second
                            dialog.dismiss()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
            }
        )
        screen.addPreference(
            SeekBarPreference(context).apply {
                key = KEY_ICON_SCALE
                title = context.getString(R.string.settings_icon_size)
                min = MIN_PERCENT
                max = MAX_PERCENT
                seekBarIncrement = STEP
                showSeekBarValue = true
                updatesContinuously = false
                isPersistent = false
                value = (iconScale(context) * 100).toInt()
                order = -19
                isIconSpaceReserved = false
                setOnPreferenceChangeListener { pref, newValue ->
                    // In 5-%-Schritten (der Regler selbst rastet nur bei Tasten ein)
                    val percent = ((newValue as Int + STEP / 2) / STEP * STEP).coerceIn(MIN_PERCENT, MAX_PERCENT)
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putInt(KEY_ICON_SCALE, percent).apply()
                    idp.reloadForLetzgo()
                    (pref as SeekBarPreference).value = percent
                    false
                }
            }
        )
    }
}
