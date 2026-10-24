package de.letzgo.launcher.model

import android.content.Context
import android.os.UserManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.SessionCommitReceiver
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MAIN_EXECUTOR

/**
 * Welche Apps auf dem Homescreen liegen, wie auf iOS: Alle Apps stehen in der App-Mediathek, der
 * Homescreen ist eine Auswahl. Neu installierte Apps kommen hinten auf den Homescreen, wenn
 * „Neue Apps zum Startbildschirm hinzufügen“ an ist; beim ersten Start einmal alle Apps.
 * Entfernte Apps bleiben weg, bis man sie aus der Mediathek zurückholt.
 */
object HomeScreenApps {

    private const val PREFS = "letzgo_home_screen"
    private const val KEY_KNOWN = "known_apps"
    // Merkliste aus Version 0.4.0 (HomeScreenRemovals), wird beim Umstieg übernommen
    private const val LEGACY_REMOVED_PREFS = "letzgo_removed_from_home"

    /** Gleicht nach dem Laden und bei jeder Paketänderung ab. */
    @JvmStatic
    fun sync(launcher: Launcher) {
        launcher.model.enqueueModelUpdateTask { controller, dataModel, apps ->
            val context = controller.context
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val users = context.getSystemService(UserManager::class.java)!!
            val installed = apps.data.associateBy { serialize(users, it.toComponentKey()) }
            val firstRun = !prefs.contains(KEY_KNOWN)
            val known = prefs.getStringSet(KEY_KNOWN, emptySet())!!

            val toAdd =
                if (firstRun) {
                    val removed = context.getSharedPreferences(LEGACY_REMOVED_PREFS, Context.MODE_PRIVATE)
                    installed.filterKeys { !removed.contains(it) }.values
                } else {
                    installed.filterKeys { it !in known }.values
                        .filter { SessionCommitReceiver.isEnabled(context, it.user) }
                }
            // Deinstallierte Apps vergessen, damit sie bei einer Neuinstallation wieder als neu gelten
            prefs.edit().putStringSet(KEY_KNOWN, installed.keys).apply()
            if (firstRun) context.deleteSharedPreferences(LEGACY_REMOVED_PREFS)
            place(controller, dataModel, toAdd)
        }
    }

    /** Legt die Apps auf den Homescreen, sofern sie dort noch nicht liegen. */
    @JvmStatic
    fun add(launcher: Launcher, apps: Collection<AppInfo>) {
        launcher.model.enqueueModelUpdateTask { controller, dataModel, _ ->
            place(controller, dataModel, apps)
        }
    }

    /** Liegt die App auf einer Seite, im Dock oder in einem Ordner? Antwort im Main-Thread. */
    @JvmStatic
    fun isOnHome(launcher: Launcher, app: AppInfo, callback: (Boolean) -> Unit) {
        launcher.model.enqueueModelUpdateTask { _, dataModel, _ ->
            val onHome = synchronized(dataModel) { app.toComponentKey() in componentsOnHome(dataModel) }
            MAIN_EXECUTOR.execute { callback(onHome) }
        }
    }

    private fun place(
        controller: ModelTaskController,
        dataModel: BgDataModel,
        apps: Collection<AppInfo>,
    ) {
        val context = controller.context
        val added = ArrayList<ItemInfo>()
        synchronized(dataModel) {
            val onHome = componentsOnHome(dataModel)
            val missing =
                apps.filter { it.toComponentKey() !in onHome }
                    .distinctBy { it.toComponentKey() }
                    .sortedBy { it.title?.toString()?.lowercase() }
            if (missing.isEmpty()) return
            val idp = InvariantDeviceProfile.INSTANCE.get(context)
            val slots = FreeSlots(idp.numColumns, idp.numRows, dataModel.itemsIdMap)
            for (app in missing) {
                val item = app.makeWorkspaceItem(context)
                val (screen, x, y) = slots.next()
                item.container = CONTAINER_DESKTOP
                item.screenId = screen
                item.cellX = x
                item.cellY = y
                item.spanX = 1
                item.spanY = 1
                added += item
            }
            controller.getModelWriter().addItemsToDatabase(added)
        }
        controller.scheduleCallbackTask { it.bindItemsAdded(added) }
    }

    private fun componentsOnHome(dataModel: BgDataModel): Set<ComponentKey> {
        val result = HashSet<ComponentKey>()
        for (info in dataModel.itemsIdMap) {
            val inHomeContainer =
                info.container == CONTAINER_DESKTOP ||
                    info.container == CONTAINER_HOTSEAT ||
                    info.container > 0 // Ordner
            if (inHomeContainer && info.itemType == ITEM_TYPE_APPLICATION) {
                info.targetComponent?.let { result += ComponentKey(it, info.user) }
            }
        }
        return result
    }

    /** Liefert freie Zellen zeilenweise ab der letzten belegten Seite. */
    private class FreeSlots(private val columns: Int, private val rows: Int, items: Iterable<ItemInfo>) {

        private val occupied = HashMap<Int, Array<BooleanArray>>()
        private var screen: Int
        private var index = 0

        init {
            for (info in items) {
                if (info.container != CONTAINER_DESKTOP) continue
                val grid = gridFor(info.screenId)
                for (x in info.cellX until (info.cellX + info.spanX).coerceAtMost(columns)) {
                    for (y in info.cellY until (info.cellY + info.spanY).coerceAtMost(rows)) {
                        if (x >= 0 && y >= 0) grid[y][x] = true
                    }
                }
            }
            screen = occupied.keys.maxOrNull() ?: 0
        }

        fun next(): Triple<Int, Int, Int> {
            while (true) {
                val grid = gridFor(screen)
                while (index < columns * rows) {
                    val x = index % columns
                    val y = index / columns
                    index++
                    if (!grid[y][x]) {
                        grid[y][x] = true
                        return Triple(screen, x, y)
                    }
                }
                screen++
                index = 0
            }
        }

        private fun gridFor(screenId: Int) =
            occupied.getOrPut(screenId) { Array(rows) { BooleanArray(columns) } }
    }

    private fun serialize(users: UserManager, key: ComponentKey) =
        "${key.componentName.flattenToString()}#${users.getSerialNumberForUser(key.user)}"

    private fun AppInfo.toComponentKey() = ComponentKey(componentName!!, user)
}
