package de.letzgo.launcher.settings

import android.app.role.RoleManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY
import com.android.launcher3.settings.SettingsActivity.LauncherSettingsFragment
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import de.letzgo.launcher.update.AppUpdate
import de.letzgo.launcher.update.AppUpdateDialog

/** Launcher3-Einstellungen plus „Als Standard festlegen“ und „Nach Updates suchen“. */
class LetzgoSettingsFragment : LauncherSettingsFragment() {

    private var updateDialog: AppUpdateDialog? = null
    private var checking = false
    private val requestHomeRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateDefaultHomePreference()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        val screen = preferenceScreen
        // Neue Apps landen ohnehin immer auf dem Homescreen (AllAppsOnHomeScreen)
        screen.findPreference<Preference>(ADD_ICON_PREFERENCE_KEY)?.let(screen::removePreference)
        screen.addPreference(
            Preference(requireContext()).apply {
                key = KEY_DEFAULT_HOME
                title = getString(R.string.set_default_home)
                summary = getString(R.string.set_default_home_summary)
                isPersistent = false
                order = -1
                setOnPreferenceClickListener {
                    val roles = requireContext().getSystemService(RoleManager::class.java)
                    requestHomeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    true
                }
            }
        )
        screen.addPreference(
            Preference(requireContext()).apply {
                key = KEY_UPDATE
                title = getString(R.string.update_check)
                summary = versionSummary()
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
        updateDefaultHomePreference()
        updateDialog?.onResume()
    }

    /** Nur anbieten, solange letzgo nicht der Standard-Startbildschirm ist. */
    private fun updateDefaultHomePreference() {
        val roles = requireContext().getSystemService(RoleManager::class.java)
        findPreference<Preference>(KEY_DEFAULT_HOME)?.isVisible =
            roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun checkForUpdate() {
        if (checking) return
        checking = true
        val preference = findPreference<Preference>(KEY_UPDATE)
        preference?.summary = getString(R.string.update_checking)
        THREAD_POOL_EXECUTOR.execute {
            val result = runCatching { AppUpdate.newer() }
            MAIN_EXECUTOR.execute {
                checking = false
                val activity = activity ?: return@execute
                preference?.summary = versionSummary()
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

    private fun versionSummary() =
        getString(R.string.update_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

    private companion object {
        const val KEY_UPDATE = "letzgo_update"
        const val KEY_DEFAULT_HOME = "letzgo_default_home"
    }
}
