package de.letzgo.launcher.model

import android.content.ComponentName
import android.content.Context
import com.android.launcher3.model.data.AppInfo
import kotlin.math.pow

/**
 * Merkt sich, welche Apps aus dem Launcher gestartet werden, für die Kachel „Vorschläge“ der
 * App-Mediathek (wie auf iOS). Häufig und kürzlich gestartete Apps stehen vorn; ältere Starts
 * zählen mit einer Halbwertszeit von einer Woche weniger.
 */
object AppUsage {

    private const val PREFS = "letzgo_usage"
    private const val HALF_LIFE_DAYS = 7.0
    private const val DAY_MS = 24 * 60 * 60 * 1000.0

    fun record(context: Context, component: ComponentName) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = component.flattenToShortString()
        val now = System.currentTimeMillis()
        val score = score(prefs.getString(key, null), now) + 1
        prefs.edit().putString(key, "$score,$now").apply()
    }

    /** Die [count] meistgenutzten Apps aus [apps], ohne nie gestartete. */
    fun suggestions(context: Context, apps: List<AppInfo>, count: Int): List<AppInfo> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        return apps
            .mapNotNull { app ->
                val key = app.componentName?.flattenToShortString() ?: return@mapNotNull null
                score(prefs.getString(key, null), now).takeIf { it > 0 }?.let { app to it }
            }
            .sortedByDescending { it.second }
            .take(count)
            .map { it.first }
    }

    /** Gespeicherter Wert „Punkte,Zeit“ auf jetzt abgewertet. */
    private fun score(stored: String?, now: Long): Double {
        val parts = stored?.split(',') ?: return 0.0
        val points = parts.getOrNull(0)?.toDoubleOrNull() ?: return 0.0
        val time = parts.getOrNull(1)?.toLongOrNull() ?: return 0.0
        return points * 0.5.pow((now - time) / DAY_MS / HALF_LIFE_DAYS)
    }
}
