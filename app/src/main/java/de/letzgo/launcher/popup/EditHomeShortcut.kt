package de.letzgo.launcher.popup

import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
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
        @JvmField
        val FACTORY =
            SystemShortcut.Factory<ActivityContext> { target, itemInfo, originalView ->
                EditHomeShortcut(target, itemInfo, originalView)
            }
    }
}
