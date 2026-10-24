package de.letzgo.launcher.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuItemCompat
import de.letzgo.launcher.LetzgoLauncher
import com.google.android.material.R as MaterialR

/**
 * Kontextmenü nach Material 3 am Element verankert (wie die Kontextmenüs der Apps), statt eines
 * Dialogs. Solange es offen ist, tritt der Hintergrund zurück ([Backdrop]).
 */
object M3Menu {

    class Item(
        @DrawableRes val icon: Int?,
        val title: CharSequence,
        /** null = keine Auswahl; sonst gehört der Eintrag zu einer Auswahlgruppe (z. B. Größen). */
        val checked: Boolean? = null,
        val destructive: Boolean = false,
        val action: () -> Unit,
    )

    fun show(anchor: View, items: List<Item>) {
        val m3 = M3.context(anchor.context)
        val iconTint = ColorStateList.valueOf(M3.color(m3, MaterialR.attr.colorOnSurfaceVariant))
        val error = M3.color(m3, android.R.attr.colorError)
        val popup = PopupMenu(m3, anchor, Gravity.NO_GRAVITY)
        items.forEachIndexed { index, item ->
            val title =
                if (item.destructive) {
                    SpannableString(item.title).apply {
                        setSpan(ForegroundColorSpan(error), 0, length, 0)
                    }
                } else {
                    item.title
                }
            val group = if (item.checked != null) CHOICE_GROUP else 0
            popup.menu.add(group, index, index, title).apply {
                item.icon?.let(::setIcon)
                MenuItemCompat.setIconTintList(
                    this,
                    if (item.destructive) ColorStateList.valueOf(error) else iconTint,
                )
            }
        }
        popup.menu.setGroupCheckable(CHOICE_GROUP, true, true)
        items.forEachIndexed { index, item -> if (item.checked == true) popup.menu.findItem(index).isChecked = true }
        popup.setForceShowIcon(true)
        popup.setOnMenuItemClickListener { menuItem ->
            items[menuItem.itemId].action()
            true
        }
        val backdrop = launcherOf(anchor.context)?.backdrop
        backdrop?.set(Backdrop.POPUP, 1f)
        popup.setOnDismissListener { backdrop?.set(Backdrop.POPUP, 0f) }
        popup.show()
    }

    private const val CHOICE_GROUP = 1

    private fun launcherOf(context: Context): LetzgoLauncher? {
        var c: Context? = context
        while (c != null) {
            if (c is LetzgoLauncher) return c
            c = (c as? ContextWrapper)?.baseContext
        }
        return null
    }
}
