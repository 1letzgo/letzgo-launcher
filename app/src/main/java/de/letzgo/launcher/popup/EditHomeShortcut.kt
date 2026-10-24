package de.letzgo.launcher.popup

import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.PopupCategory
import com.android.launcher3.popup.PopupData
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext
import de.letzgo.launcher.LetzgoLauncher

/** „Home-Bildschirm bearbeiten“ im Kontextmenü: startet den Bearbeitungsmodus (Wackeln). */
class EditHomeShortcut(target: ActivityContext, itemInfo: ItemInfo, originalView: View) :
    SystemShortcut<ActivityContext>(
        R.drawable.enter_home_gardening_icon,
        R.string.edit_home_screen_ios,
        target,
        itemInfo,
        originalView,
        false,
    ) {

    override fun onClick(view: View) {
        AbstractFloatingView.closeAllOpenViews(mTarget)
        (mTarget as? LetzgoLauncher)?.jiggleMode?.enter()
    }

    companion object {
        /** Gleicher Eintrag im Menü für Ordner und Widgets (Launcher3-PopupData). */
        @JvmField
        val POPUP_DATA =
            PopupData(
                iconResId = R.drawable.enter_home_gardening_icon,
                labelResId = R.string.edit_home_screen_ios,
                category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
            ) { target, _, _ ->
                AbstractFloatingView.closeAllOpenViews(target)
                (target as? LetzgoLauncher)?.jiggleMode?.enter()
            }

        @JvmField
        val FACTORY =
            SystemShortcut.Factory<ActivityContext> { target, itemInfo, originalView ->
                EditHomeShortcut(target, itemInfo, originalView)
            }
    }
}
