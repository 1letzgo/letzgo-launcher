package de.letzgo.launcher.ui

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.popup.PopupCategory
import com.android.launcher3.popup.PopupContainer
import com.android.launcher3.popup.PopupData
import de.letzgo.launcher.LetzgoLauncher

/**
 * Context menu anchored at an element, drawn by the same Launcher3 popup as the context menus of
 * home screen icons (rounded M3 surface, arrow towards the element). The backdrop dims while it is
 * open (see LetzgoLauncher.onDragLayerHierarchyChanged).
 */
object M3Menu {

    /**
     * @param checked null = plain entry; true = the current choice of a group (e.g. widget sizes),
     * which is left out because choosing it would change nothing
     */
    class Item(
        @DrawableRes val icon: Int,
        @StringRes val label: Int,
        val checked: Boolean? = null,
        val destructive: Boolean = false,
        val action: () -> Unit,
    )

    fun show(anchor: View, items: List<Item>) {
        val launcher = Launcher.getLauncher(anchor.context) ?: return
        val entries =
            items.filter { it.checked != true }.map { item ->
                PopupData(item.icon, item.label, PopupCategory.SYSTEM_SHORTCUT) { target, _, _ ->
                    AbstractFloatingView.closeAllOpenViews(target)
                    item.action()
                }
            }
        (launcher as? LetzgoLauncher)?.popupAnchor = anchor
        PopupContainer.showForMenuItems(launcher, anchor, entries)
    }
}
