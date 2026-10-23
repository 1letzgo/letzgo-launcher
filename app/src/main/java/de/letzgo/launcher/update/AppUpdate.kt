package de.letzgo.launcher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import com.android.launcher3.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray

/**
 * Updates außerhalb von Google Play (wie in abstand): neue Versionen liegen als Releases im
 * öffentlichen Repo 1letzgo/letzgo-launcher, Tag `launcher-android-<versionCode>` mit der APK als
 * Asset. Die App lädt die APK selbst (Prüfsumme gegen den GitHub-Digest) und übergibt sie dem
 * Paketinstaller.
 */
object AppUpdate {
    private const val RELEASES =
        "https://api.github.com/repos/1letzgo/letzgo-launcher/releases?per_page=30"
    const val TAG_PREFIX = "launcher-android-"
    private const val PREFS = "letzgo_update"
    private const val LAST_CHECK = "last_check"
    private const val SKIPPED = "skipped_version_code"
    private const val CHECK_INTERVAL_MS = 6 * 3600_000L
    private const val TIMEOUT_MS = 30_000

    /** [sha256] aus dem Asset-Digest von GitHub, zum Prüfen des Downloads. */
    data class Release(
        val versionCode: Int,
        val name: String,
        val notes: String,
        val apkUrl: String,
        val pageUrl: String,
        val sha256: String?,
    )

    /** Neueste veröffentlichte Version aus der Release-Liste, wenn sie neuer ist als [installedCode]. */
    fun newest(releasesJson: String, installedCode: Int): Release? {
        val releases = JSONArray(releasesJson)
        return (0 until releases.length())
            .map(releases::getJSONObject)
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
            .mapNotNull { r ->
                val tag = r.optString("tag_name")
                if (!tag.startsWith(TAG_PREFIX)) return@mapNotNull null
                val code = tag.removePrefix(TAG_PREFIX).toIntOrNull() ?: return@mapNotNull null
                val assets = r.optJSONArray("assets") ?: return@mapNotNull null
                val apk =
                    (0 until assets.length())
                        .map(assets::getJSONObject)
                        .firstOrNull { it.optString("name").endsWith(".apk") }
                        ?: return@mapNotNull null
                Release(
                    versionCode = code,
                    name = r.optString("name").ifEmpty { tag },
                    notes = r.optString("body").trim(),
                    apkUrl = apk.getString("browser_download_url"),
                    pageUrl = r.optString("html_url"),
                    sha256 = apk.optString("digest").takeIf { it.isNotEmpty() }?.removePrefix("sha256:"),
                )
            }
            .maxByOrNull { it.versionCode }
            ?.takeIf { it.versionCode > installedCode }
    }

    /** Fragt GitHub; neuere Version oder `null`. Wirft bei Netzfehlern. */
    @WorkerThread
    fun newer(): Release? {
        val connection = open(RELEASES).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val body =
            connection.useConnection {
                if (responseCode != HttpURLConnection.HTTP_OK) error("HTTP $responseCode")
                inputStream.bufferedReader().readText()
            }
        return newest(body, BuildConfig.VERSION_CODE)
    }

    /**
     * Beim Start höchstens alle sechs Stunden, nur in Release-Builds und nicht für eine Version, die
     * „Später“ bekam.
     */
    @WorkerThread
    fun checkOnLaunch(context: Context): Release? {
        if (!BuildConfig.UPDATE_CHECK) return null
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(LAST_CHECK, 0) < CHECK_INTERVAL_MS) return null
        val release = runCatching { newer() }.getOrNull()
        prefs.edit().putLong(LAST_CHECK, now).apply()
        return release?.takeIf { it.versionCode > prefs.getInt(SKIPPED, 0) }
    }

    fun skip(context: Context, release: Release) =
        prefs(context).edit().putInt(SKIPPED, release.versionCode).apply()

    /** Lädt die APK nach `cache/updates/` und prüft sie gegen den Digest von GitHub. */
    @WorkerThread
    fun download(context: Context, release: Release, progress: (Float?) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, "letzgo-launcher-${release.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        // Mobil kann die APK deutlich länger brauchen als die API-Abfrage
        val connection = open(release.apkUrl).apply { readTimeout = 10 * 60_000 }
        connection.useConnection {
            if (responseCode != HttpURLConnection.HTTP_OK) error("HTTP $responseCode")
            val total = contentLengthLong
            val buffer = ByteArray(64 * 1024)
            var done = 0L
            inputStream.use { input ->
                file.outputStream().use { out ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        progress(total.takeIf { it > 0 }?.let { done.toFloat() / it })
                    }
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && !release.sha256.equals(hash, ignoreCase = true)) {
            file.delete()
            error("Prüfsumme stimmt nicht")
        }
        return file
    }

    /** Darf die App Pakete installieren? Sonst erst die Freigabe in den Android-Einstellungen. */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) =
        context.startActivity(
            Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

    /** Übergibt die APK dem Paketinstaller; Android fragt dann „Installieren?“. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun open(url: String) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

    private inline fun <T> HttpURLConnection.useConnection(block: HttpURLConnection.() -> T): T =
        try {
            block()
        } finally {
            disconnect()
        }
}
