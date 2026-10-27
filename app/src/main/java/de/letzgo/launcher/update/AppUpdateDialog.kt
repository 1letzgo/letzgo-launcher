package de.letzgo.launcher.update

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import de.letzgo.launcher.ui.M3
import de.letzgo.launcher.ui.dp
import com.google.android.material.R as MaterialR
import java.io.File

/**
 * Hinweis auf eine neue Version (wie in abstand): lädt die APK in der App (mit Fortschritt und
 * Prüfsumme) und übergibt sie dem Paketinstaller. Fehlt die Freigabe „Unbekannte Apps
 * installieren“, führt der Dialog dorthin; [onResume] installiert, sobald man zurückkommt.
 */
class AppUpdateDialog(
    private val activity: Activity,
    private val release: AppUpdate.Release,
    private val onDismiss: () -> Unit = {},
) {

    private val m3 = M3.context(activity)
    private val status =
        TextView(m3).apply {
            M3.text(this, M3.Type.BODY_MEDIUM, M3.color(m3, MaterialR.attr.colorOnSurfaceVariant))
            visibility = View.GONE
        }
    private val progressBar =
        LinearProgressIndicator(m3).apply {
            max = PROGRESS_MAX
            visibility = View.GONE
        }
    private var dialog: AlertDialog? = null
    private var downloading = false
    private var file: File? = null
    private var failed = false
    private var waitingForPermission = false

    fun show() {
        val padding = m3.dp(24)
        val content =
            LinearLayout(m3).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding / 2, padding, 0)
                addView(TextView(m3).apply {
                    text = release.name
                    M3.text(this, M3.Type.TITLE_MEDIUM, M3.color(m3, MaterialR.attr.colorOnSurface))
                })
                if (release.notes.isNotEmpty()) {
                    addView(TextView(m3).apply {
                        text = release.notes
                        M3.text(this, M3.Type.BODY_MEDIUM, M3.color(m3, MaterialR.attr.colorOnSurfaceVariant))
                        setPadding(0, m3.dp(8), 0, 0)
                    })
                }
                addView(status.apply { setPadding(0, m3.dp(8), 0, 0) })
                addView(progressBar)
            }
        dialog =
            MaterialAlertDialogBuilder(m3)
                .setTitle(R.string.update_title)
                .setView(content)
                .setPositiveButton(R.string.update_action, null)
                .setNegativeButton(R.string.update_later, null)
                .setCancelable(false)
                .setOnDismissListener { onDismiss() }
                .show()
                .apply {
                    // Eigene Listener, damit der Dialog während Download und Installation offen bleibt
                    getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { onConfirm() }
                    getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener { onLater() }
                }
    }

    /** Zurück aus den Einstellungen: mit Freigabe gleich installieren. */
    fun onResume() {
        val apk = file ?: return
        if (waitingForPermission && AppUpdate.canInstall(activity)) installOrAsk(apk)
    }

    private fun onConfirm() {
        val apk = file
        if (apk != null) {
            installOrAsk(apk)
            return
        }
        downloading = true
        failed = false
        update()
        THREAD_POOL_EXECUTOR.execute {
            val result = runCatching {
                AppUpdate.download(activity, release) { fraction ->
                    MAIN_EXECUTOR.execute { setProgress(fraction) }
                }
            }
            MAIN_EXECUTOR.execute {
                downloading = false
                result
                    .onSuccess {
                        file = it
                        installOrAsk(it)
                    }
                    .onFailure { failed = true }
                update()
            }
        }
    }

    private fun onLater() {
        if (downloading) return
        if (file == null) AppUpdate.skip(activity, release)
        dialog?.dismiss()
    }

    private fun installOrAsk(apk: File) {
        if (AppUpdate.canInstall(activity)) {
            waitingForPermission = false
            AppUpdate.install(activity, apk)
        } else {
            waitingForPermission = true
            AppUpdate.openInstallPermission(activity)
        }
        update()
    }

    private fun setProgress(fraction: Float?) {
        progressBar.isIndeterminate = fraction == null
        if (fraction != null) progressBar.progress = (fraction * PROGRESS_MAX).toInt()
    }

    private fun update() {
        val dialog = dialog ?: return
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).apply {
            isEnabled = !downloading
            setText(
                when {
                    file != null -> R.string.update_install
                    failed -> R.string.update_retry
                    else -> R.string.update_action
                }
            )
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = !downloading
        progressBar.visibility = if (downloading) View.VISIBLE else View.GONE
        val message =
            when {
                failed -> R.string.update_failed
                downloading -> R.string.update_downloading
                waitingForPermission -> R.string.update_permission
                file != null -> R.string.update_downloaded
                else -> null
            }
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let(status::setText)
    }

    private companion object {
        const val PROGRESS_MAX = 1000
    }
}
