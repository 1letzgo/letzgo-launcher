package de.letzgo.launcher.compat

import android.app.PendingIntent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * App-Lock-APIs aus Android 17, die nicht im öffentlichen SDK liegen. Aufruf per Reflection;
 * fehlt die Methode auf dem Gerät, verhält sich alles wie "nicht unterstützt".
 */
object AppLockCompat {

    @JvmStatic
    fun isAppLockSupported(info: ApplicationInfo): Boolean = info.callBoolean("isAppLockSupported")

    @JvmStatic
    fun isAppLockEnabled(info: ApplicationInfo): Boolean = info.callBoolean("isAppLockEnabled")

    @JvmStatic
    fun getEnableAppLockIntentForPackage(
        pm: PackageManager,
        packageName: String,
        enabled: Boolean,
    ): PendingIntent? =
        runCatching {
                pm.javaClass
                    .getMethod(
                        "getEnableAppLockIntentForPackage",
                        String::class.java,
                        Boolean::class.javaPrimitiveType,
                    )
                    .invoke(pm, packageName, enabled) as PendingIntent?
            }
            .getOrNull()

    private fun Any.callBoolean(name: String): Boolean =
        runCatching { javaClass.getMethod(name).invoke(this) as Boolean }.getOrDefault(false)
}
