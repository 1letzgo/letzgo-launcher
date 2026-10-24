package de.letzgo.launcher.edit

import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.SecondaryDropTarget
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.letzgo.launcher.ui.M3

/** Entfernen von Homescreen-Elementen wie auf iOS, aus Kontextmenü und Minus-Badge. */
object RemoveItem {

    /**
     * Apps: fragt nach „App löschen“ oder „Vom Home-Bildschirm entfernen“. Ordner: fragt nach
     * „Ordner entfernen“. Andere Elemente (Widgets, Shortcuts) werden direkt entfernt.
     */
    @JvmStatic
    fun confirm(target: ActivityContext, item: ItemInfo, view: View) {
        AbstractFloatingView.closeAllOpenViewsExcept(target, TYPE_FOLDER)
        val context = view.context
        when {
            item.itemType == ITEM_TYPE_APPLICATION -> {
                val uninstallTarget = SecondaryDropTarget.getUninstallTarget(context, item)
                dialog(context)
                    .setTitle(context.getString(R.string.remove_app_title, item.title))
                    .setMessage(R.string.remove_app_message)
                    .setPositiveButton(R.string.remove_from_home) { _, _ ->
                        removeFromHome(target, item, view)
                    }
                    .apply {
                        if (uninstallTarget != null) {
                            setNeutralButton(R.string.delete_app) { _, _ ->
                                SecondaryDropTarget.performUninstall(context, uninstallTarget, item)
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            item is CollectionInfo ->
                dialog(context)
                    .setTitle(context.getString(R.string.remove_folder_title, item.title))
                    .setMessage(R.string.remove_folder_message)
                    .setPositiveButton(R.string.remove_folder) { _, _ ->
                        removeFromHome(target, item, view)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            else -> removeFromHome(target, item, view)
        }
    }

    /** Wie Launcher3s „Entfernen“: mit „Rückgängig“-Hinweis. */
    private fun removeFromHome(target: ActivityContext, item: ItemInfo, view: View) {
        val handler = target.dropTargetHandler
        handler.prepareToUndoDelete(item)
        handler.onDeleteComplete(item, view)
    }

    private fun dialog(context: android.content.Context) = MaterialAlertDialogBuilder(M3.context(context))
}
