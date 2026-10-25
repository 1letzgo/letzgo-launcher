package de.letzgo.launcher.edit

import android.content.Context
import com.android.launcher3.Workspace

/**
 * Default home screen page: shown after start and when pressing home. Stored as the screen ID of
 * the page, so it follows the page when pages are reordered (see [PageOverview]).
 */
object DefaultPage {

    private const val PREFS = "letzgo_pages"
    private const val KEY_SCREEN = "default_screen_id"

    fun screenId(context: Context): Int? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SCREEN, -1).takeIf { it >= 0 }

    fun setScreenId(context: Context, screenId: Int?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (screenId == null) remove(KEY_SCREEN) else putInt(KEY_SCREEN, screenId)
        }.apply()
    }

    /** Page index of the default page, or the first page if it is not set or no longer exists. */
    @JvmStatic
    fun index(workspace: Workspace<*>): Int {
        val id = screenId(workspace.context) ?: return Workspace.DEFAULT_PAGE
        return workspace.getPageIndexForScreenId(id).takeIf { it >= 0 } ?: Workspace.DEFAULT_PAGE
    }
}
