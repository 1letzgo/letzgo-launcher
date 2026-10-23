package de.letzgo.launcher.popup

import android.app.AlertDialog
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_FOLDER
import com.android.launcher3.R
import com.android.launcher3.SecondaryDropTarget
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext

/**
 * „App entfernen“ wie auf iOS: fragt, ob die App gelöscht oder nur vom Homescreen entfernt
 * werden soll. Ersetzt [SystemShortcut.REMOVE] für App-Icons.
 */
class RemoveAppShortcut(target: ActivityContext, itemInfo: ItemInfo, originalView: View) :
    SystemShortcut<ActivityContext>(
        R.drawable.ic_remove_no_shadow,
        R.string.remove_app,
        target,
        itemInfo,
        originalView,
        false,
    ) {

    override fun onClick(view: View) {
        AbstractFloatingView.closeAllOpenViewsExcept(mTarget, TYPE_FOLDER)
        val context = view.context
        val uninstallTarget = SecondaryDropTarget.getUninstallTarget(context, mItemInfo)
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(context.getString(R.string.remove_app_title, mItemInfo.title))
            .setPositiveButton(R.string.remove_from_home) { _, _ -> removeFromHome() }
            .apply {
                if (uninstallTarget != null) {
                    setNeutralButton(R.string.delete_app) { _, _ ->
                        SecondaryDropTarget.performUninstall(context, uninstallTarget, mItemInfo)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Wie [SystemShortcut.RemoveApp]: mit „Rückgängig“-Hinweis. */
    private fun removeFromHome() {
        val handler = mTarget.dropTargetHandler
        handler.prepareToUndoDelete(mItemInfo)
        handler.onDeleteComplete(mItemInfo, mOriginalView)
    }

    companion object {
        @JvmField
        val FACTORY =
            SystemShortcut.Factory<ActivityContext> { target, itemInfo, originalView ->
                RemoveAppShortcut(target, itemInfo, originalView)
            }
    }
}
