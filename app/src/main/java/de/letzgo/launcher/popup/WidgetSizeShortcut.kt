package de.letzgo.launcher.popup

import android.appwidget.AppWidgetManager
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.CellLayout
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.popup.PopupCategory
import com.android.launcher3.popup.PopupData
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.util.WidgetSizeHandler.Companion.updateSizeRanges
import de.letzgo.launcher.LetzgoApplication
import de.letzgo.launcher.ui.M3

/**
 * Widget-Größen wie auf iOS im Kontextmenü eines Homescreen-Widgets: klein (2 x 2), mittel (4 x 2)
 * und groß (4 x 4) bei vier Spalten. Andere Elemente rücken dabei wie beim Ziehen am Rahmen zur
 * Seite ([CellLayout.createAreaForResize]).
 */
object WidgetSizeShortcut {

    private enum class Size(val label: Int, val icon: Int, val wide: Boolean, val tall: Boolean) {
        SMALL(R.string.widget_size_small, R.drawable.ic_widget_width_decrease, wide = false, tall = false),
        MEDIUM(R.string.widget_size_medium, R.drawable.ic_widget_width_increase, wide = true, tall = false),
        LARGE(R.string.widget_size_large, R.drawable.ic_widget_height_increase, wide = true, tall = true),
    }

    private val entries =
        Size.entries.associateWith { size ->
            PopupData(
                iconResId = size.icon,
                labelResId = size.label,
                category = PopupCategory.SYSTEM_SHORTCUT_FIXED,
            ) { target, _, view ->
                AbstractFloatingView.closeAllOpenViews(target)
                (view as? LauncherAppWidgetHostView)?.let { resize(target, it, size) }
            }
        }

    /** Größen außer der aktuellen, vor die übrigen Einträge. */
    @JvmStatic
    fun withSizes(itemInfo: ItemInfo, items: List<PopupData>): List<PopupData> {
        if (itemInfo !is LauncherAppWidgetInfo) return items
        val context = LetzgoApplication.appContext
        val provider =
            AppWidgetManager.getInstance(context).getAppWidgetInfo(itemInfo.appWidgetId)?.let {
                LauncherAppWidgetProviderInfo.fromProviderInfo(context, it)
            }
        val columns = InvariantDeviceProfile.INSTANCE.get(context).numColumns
        return Size.entries
            .filter { spansOf(it, columns) != (itemInfo.spanX to itemInfo.spanY) }
            .filter { provider == null || supports(provider, spansOf(it, columns)) }
            .map { entries.getValue(it) } + items
    }

    private fun supports(provider: LauncherAppWidgetProviderInfo, spans: Pair<Int, Int>) =
        spans.first >= provider.minSpanX && spans.second >= provider.minSpanY &&
            spans.first <= provider.maxSpanX && spans.second <= provider.maxSpanY

    private fun spansOf(size: Size, columns: Int): Pair<Int, Int> {
        val half = columns / 2
        return (if (size.wide) columns else half) to (if (size.tall) columns else half)
    }

    private fun resize(target: ActivityContext, widget: LauncherAppWidgetHostView, size: Size) {
        val cellLayout = widget.parent?.parent as? CellLayout ?: return
        val lp = widget.layoutParams as? CellLayoutLayoutParams ?: return
        val (spanX, spanY) = spansOf(size, cellLayout.countX)
        val provider = widget.appWidgetInfo as? LauncherAppWidgetProviderInfo
        val fits = spanY <= cellLayout.countY && (provider == null || supports(provider, spanX to spanY))
        if (!fits) {
            M3.snackbar(cellLayout, R.string.widget_size_unsupported)
            return
        }
        // Am alten Ort bleiben, soweit die neue Größe auf die Seite passt
        val cellX = lp.cellX.coerceAtMost(cellLayout.countX - spanX).coerceAtLeast(0)
        val cellY = lp.cellY.coerceAtMost(cellLayout.countY - spanY).coerceAtLeast(0)
        val direction = intArrayOf(1, 1)
        if (!cellLayout.createAreaForResize(cellX, cellY, spanX, spanY, widget, direction, false)) {
            M3.snackbar(cellLayout, R.string.widget_size_no_space)
            return
        }
        lp.tmpCellX = cellX
        lp.tmpCellY = cellY
        lp.cellHSpan = spanX
        lp.cellVSpan = spanY
        cellLayout.createAreaForResize(cellX, cellY, spanX, spanY, widget, direction, true)
        widget.updateSizeRanges(spanX, spanY)
        widget.requestLayout()
    }

}
