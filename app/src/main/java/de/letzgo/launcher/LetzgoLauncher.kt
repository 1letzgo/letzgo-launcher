package de.letzgo.launcher

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.popup.SystemShortcut.APP_INFO
import com.android.launcher3.popup.SystemShortcut.INSTALL
import com.android.launcher3.popup.SystemShortcut.WIDGETS
import com.android.launcher3.util.TouchController
import de.letzgo.launcher.model.AllAppsOnHomeScreen
import de.letzgo.launcher.model.HomeScreenRemovals
import de.letzgo.launcher.popup.RemoveAppShortcut
import de.letzgo.launcher.ui.DockBackground
import java.util.stream.Stream

/**
 * Launcher-Activity mit iOS-Verhalten: kein App-Drawer, alle Apps liegen auf den
 * Homescreen-Seiten.
 */
class LetzgoLauncher : Launcher() {

    private val syncAllApps = Runnable { AllAppsOnHomeScreen.sync(this) }
    private val appsUpdateListener = AllAppsStore.OnUpdateListener(::scheduleSync)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Feuert nach dem Laden und bei jeder Paketänderung
        appsView.appsStore.addUpdateListener(appsUpdateListener)
    }

    override fun onDestroy() {
        appsView.appsStore.removeUpdateListener(appsUpdateListener)
        dragLayer.removeCallbacks(syncAllApps)
        super.onDestroy()
    }

    override fun setupViews() {
        super.setupViews()
        DockBackground(hotseat).attach()
        showPageDots()
    }

    override fun onStateSetStart(state: LauncherState) {
        super.onStateSetStart(state)
        showPageDots()
    }

    /** Seitenpunkte über dem Dock bleiben sichtbar und sind immer weiß (Launcher3 blendet sie aus). */
    private fun showPageDots() {
        workspace.pageIndicator.setShouldAutoHide(false)
        workspace.pageIndicator.setPaintColor(Color.WHITE)
    }

    private fun scheduleSync() {
        dragLayer.removeCallbacks(syncAllApps)
        dragLayer.postDelayed(syncAllApps, SYNC_DELAY_MS)
    }

    /** Ohne [com.android.launcher3.touch.AllAppsSwipeController]: Hochwischen öffnet keinen Drawer. */
    override fun createTouchControllers(): Array<TouchController> = arrayOf(dragController)

    /** Wie Launcher3, aber „Entfernen“ fragt bei Apps nach Löschen oder Vom-Homescreen-Entfernen. */
    override fun getSupportedShortcuts(itemInfo: ItemInfo): Stream<SystemShortcut.Factory<*>> {
        val inHome = itemInfo.container == CONTAINER_DESKTOP || itemInfo.container == CONTAINER_HOTSEAT
        if (!inHome || itemInfo.itemType != ITEM_TYPE_APPLICATION) {
            return super.getSupportedShortcuts(itemInfo)
        }
        return Stream.of(APP_INFO, WIDGETS, INSTALL, RemoveAppShortcut.FACTORY)
    }

    override fun removeItem(
        v: View?,
        itemInfo: ItemInfo,
        deleteFromDb: Boolean,
        reason: String?,
    ): Boolean {
        if (deleteFromDb) HomeScreenRemovals.get(this).onRemovedFromHome(itemInfo)
        return super.removeItem(v, itemInfo, deleteFromDb, reason)
    }

    private companion object {
        const val SYNC_DELAY_MS = 300L
    }
}
