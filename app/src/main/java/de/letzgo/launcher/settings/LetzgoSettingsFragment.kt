package de.letzgo.launcher.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY
import com.android.launcher3.settings.SettingsActivity.LauncherSettingsFragment
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import de.letzgo.launcher.update.AppUpdate
import de.letzgo.launcher.update.AppUpdateDialog

/** Launcher3-Einstellungen plus „Nach Updates suchen“. */
class LetzgoSettingsFragment : LauncherSettingsFragment() {

    private var updateDialog: AppUpdateDialog? = null
    private var checking = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        val screen = preferenceScreen
        // Neue Apps landen ohnehin immer auf dem Homescreen (AllAppsOnHomeScreen)
        screen.findPreference<Preference>(ADD_ICON_PREFERENCE_KEY)?.let(screen::removePreference)
        screen.addPreference(
            Preference(requireContext()).apply {
                key = KEY_UPDATE
                title = getString(R.string.update_check)
                summary = getString(R.string.update_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                isPersistent = false
                setOnPreferenceClickListener {
                    checkForUpdate()
                    true
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        updateDialog?.onResume()
    }

    private fun checkForUpdate() {
        if (checking) return
        checking = true
        THREAD_POOL_EXECUTOR.execute {
            val result = runCatching { AppUpdate.newer() }
            MAIN_EXECUTOR.execute {
                checking = false
                val activity = activity ?: return@execute
                result
                    .onSuccess { release ->
                        if (release == null) {
                            Toast.makeText(activity, R.string.update_none, Toast.LENGTH_SHORT).show()
                        } else {
                            updateDialog =
                                AppUpdateDialog(activity, release) { updateDialog = null }
                                    .also { it.show() }
                        }
                    }
                    .onFailure {
                        Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private companion object {
        const val KEY_UPDATE = "letzgo_update"
    }
}
