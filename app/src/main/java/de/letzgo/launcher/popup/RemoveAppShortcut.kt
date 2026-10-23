package de.letzgo.launcher.popup

import android.view.View
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext
import de.letzgo.launcher.edit.RemoveItem

/**
 * „App entfernen“ wie auf iOS: fragt, ob die App gelöscht oder nur vom Homescreen entfernt
 * werden soll ([RemoveItem]). Ersetzt [SystemShortcut.REMOVE] für App-Icons.
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

    override fun onClick(view: View) = RemoveItem.confirm(mTarget, mItemInfo, mOriginalView)

    companion object {
        @JvmField
        val FACTORY =
            SystemShortcut.Factory<ActivityContext> { target, itemInfo, originalView ->
                RemoveAppShortcut(target, itemInfo, originalView)
            }
    }
}
