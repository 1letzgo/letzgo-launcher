package de.letzgo.launcher.model

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey

/**
 * Wie auf iOS liegt jede App auf dem Homescreen: Apps, die weder auf einer Seite, im Dock noch in
 * einem Ordner liegen, werden hinten angefügt – ab der letzten Seite, bei Bedarf auf neuen Seiten.
 * Ausgenommen sind Apps, die der Nutzer bewusst entfernt hat ([HomeScreenRemovals]), solange er
 * sie nicht neu installiert.
 */
object AllAppsOnHomeScreen {

    @JvmStatic
    fun sync(launcher: Launcher) {
        val removals = HomeScreenRemovals.get(launcher)
        launcher.model.enqueueModelUpdateTask { controller, dataModel, apps ->
            val context = controller.context
            val added = ArrayList<ItemInfo>()
            synchronized(dataModel) {
                val onHome = HashSet<ComponentKey>()
                for (info in dataModel.itemsIdMap) {
                    val inHomeContainer =
                        info.container == CONTAINER_DESKTOP ||
                            info.container == CONTAINER_HOTSEAT ||
                            info.container > 0 // Ordner
                    if (inHomeContainer && info.itemType == ITEM_TYPE_APPLICATION) {
                        info.targetComponent?.let { onHome += ComponentKey(it, info.user) }
                    }
                }

                // Liegt eine entfernte App wieder auf dem Homescreen (z. B. nach „Rückgängig“), gilt
                // die Markierung nicht mehr
                removals.forget(onHome)

                val missing =
                    apps.data
                        .filter { it.toComponentKey() !in onHome }
                        .filter { app ->
                            val removedAt = removals.removedAt(app.toComponentKey())
                            removedAt == null || installedAt(context, app) > removedAt
                        }
                        .sortedBy { it.title?.toString()?.lowercase() }
                if (missing.isEmpty()) return@enqueueModelUpdateTask

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

    /**
     * Erstinstallation der App; nach einer Neuinstallation jünger als das Entfernen. Für Apps in
     * anderen Profilen (z. B. Arbeitsprofil) ist der Zeitpunkt ohne Systemrechte nicht lesbar.
     */
    private fun installedAt(context: Context, app: AppInfo): Long {
        if (app.user != Process.myUserHandle()) return 0
        return try {
            context.packageManager.getPackageInfo(app.componentName!!.packageName, 0).firstInstallTime
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    private fun AppInfo.toComponentKey() =
        ComponentKey(componentName!!, user)
}
