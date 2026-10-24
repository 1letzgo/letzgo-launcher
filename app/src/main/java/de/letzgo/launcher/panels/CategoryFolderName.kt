package de.letzgo.launcher.panels

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.WorkerThread
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * Name für einen neuen Ordner wie auf iOS: die Kategorie der App-Mediathek, zu der die meisten Apps
 * im Ordner gehören (z. B. „Unterhaltung“). Wird von Launcher3s FolderNameProvider gefragt.
 */
object CategoryFolderName {

    @JvmStatic
    @WorkerThread
    fun suggest(context: Context, items: List<WorkspaceItemInfo>, apps: List<AppInfo>?): String? {
        if (apps.isNullOrEmpty() || items.size < 2) return null
        val byComponent = apps.associateBy { it.componentName }
        val pm = context.packageManager
        val categories =
            items.mapNotNull { item ->
                val app = byComponent[item.targetComponent] ?: return@mapNotNull null
                val info =
                    try {
                        pm.getApplicationInfo(app.componentName!!.packageName, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                AppCategories.categorize(app, info)
            }
        val (category, count) =
            categories.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        // Nur eindeutige Fälle benennen; „Andere“ ist kein sinnvoller Ordnername
        if (category == AppCategory.OTHER || count * 2 <= items.size) return null
        return context.getString(category.title)
    }
}
