package de.letzgo.launcher.system

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log

/**
 * Öffnet Mitteilungen und Schnelleinstellungen der Systemleiste (wie Mitteilungszentrale und
 * Kontrollzentrum auf iOS). Das System bietet dafür nur Methoden des [android.app.StatusBarManager],
 * die nicht im öffentlichen SDK stehen; sie brauchen die Berechtigung EXPAND_STATUS_BAR.
 */
object SystemPanels {

    private const val TAG = "SystemPanels"

    fun openNotifications(context: Context) = call(context, "expandNotificationsPanel")

    fun openQuickSettings(context: Context) = call(context, "expandSettingsPanel")

    @SuppressLint("WrongConstant")
    private fun call(context: Context, method: String): Boolean =
        try {
            val manager = context.getSystemService("statusbar") ?: return false
            manager.javaClass.getMethod(method).invoke(manager)
            true
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "$method nicht verfügbar", e)
            false
        }
}
