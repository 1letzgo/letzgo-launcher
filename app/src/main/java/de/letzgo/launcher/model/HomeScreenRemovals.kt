package de.letzgo.launcher.model

import android.content.Context
import android.os.UserManager
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey

/**
 * Merkt sich Apps, die der Nutzer vom Homescreen entfernt hat („Vom Home-Bildschirm entfernen“),
 * mit Zeitpunkt. [AllAppsOnHomeScreen] legt sie nicht wieder an, es sei denn, die App wurde danach
 * neu installiert.
 */
class HomeScreenRemovals private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val userManager = context.getSystemService(UserManager::class.java)!!

    /** Zeitpunkt (ms) des Entfernens oder null, wenn die App nicht entfernt wurde. */
    fun removedAt(key: ComponentKey): Long? =
        serialize(key).let { if (prefs.contains(it)) prefs.getLong(it, 0) else null }

    /** Merkt App-Icons vor; bei Ordnern alle enthaltenen Apps. */
    fun onRemovedFromHome(item: ItemInfo) {
        val keys =
            when (item) {
                is CollectionInfo -> item.getAppContents().mapNotNull { it.toComponentKey() }
                else ->
                    listOfNotNull(
                        item.takeIf { it.itemType == ITEM_TYPE_APPLICATION }?.toComponentKey()
                    )
            }
        if (keys.isEmpty()) return
        val now = System.currentTimeMillis()
        prefs.edit().apply { keys.forEach { putLong(serialize(it), now) } }.apply()
    }

    /** Hebt die Markierung auf, z. B. nach „Rückgängig“ oder einer Neuinstallation. */
    fun forget(keys: Collection<ComponentKey>) {
        val present = keys.map(::serialize).filter(prefs::contains)
        if (present.isNotEmpty()) prefs.edit().apply { present.forEach(::remove) }.apply()
    }

    private fun ItemInfo.toComponentKey(): ComponentKey? =
        targetComponent?.let { ComponentKey(it, user) }

    private fun serialize(key: ComponentKey): String =
        "${key.componentName.flattenToString()}#${userManager.getSerialNumberForUser(key.user)}"

    companion object {
        private const val PREFS_NAME = "letzgo_removed_from_home"

        @Volatile private var instance: HomeScreenRemovals? = null

        @JvmStatic
        fun get(context: Context): HomeScreenRemovals =
            instance ?: synchronized(this) {
                instance ?: HomeScreenRemovals(context.applicationContext).also { instance = it }
            }
    }
}
