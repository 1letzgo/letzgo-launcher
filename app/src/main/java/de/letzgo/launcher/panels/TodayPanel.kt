package de.letzgo.launcher.panels

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.letzgo.launcher.ui.M3
import de.letzgo.launcher.ui.SearchBar
import java.util.Date
import com.google.android.material.R as MaterialR

/**
 * Heute-Seite links von der ersten Seite (Logik wie iOS, Gestaltung Material 3): Suchleiste,
 * Datum und eine Spalte mit Widgets in Karten.
 * Hat einen eigenen [AppWidgetHost]; Widgets kommen über die Widget-Auswahl des Systems dazu und
 * lassen sich per langem Drücken entfernen.
 */
class TodayPanel(launcher: Launcher, onSearch: () -> Unit) : SidePanel(launcher, fromLeft = true) {

    private val m3 = M3.context(launcher)
    private val density = resources.displayMetrics.density
    private val prefs = launcher.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
    private val widgetManager = AppWidgetManager.getInstance(launcher)
    private val host = AppWidgetHost(launcher, HOST_ID)
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // Datum als Überschrift (M3 „headline medium“)
    private val date =
        TextView(m3).apply {
            setTextColor(M3.color(m3, MaterialR.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        }
    private val widgets = LinearLayout(m3).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(m3).apply { clipToPadding = false }
    private val content = LinearLayout(m3)
    private var grid = IconGrid.of(launcher)

    // Wie auf iOS steht oben ein Suchfeld; es öffnet die Suche
    private val searchBar =
        SearchBar(m3, R.string.search_hint, editable = false).apply {
            setOnClickListener { onSearch() }
            field.setOnClickListener { onSearch() }
        }

    init {
        setBackgroundColor(M3.color(m3, MaterialR.attr.colorSurface))
        val edit =
            MaterialButton(m3).apply {
                setText(R.string.today_add_widget)
                backgroundTintList =
                    ColorStateList.valueOf(M3.color(m3, MaterialR.attr.colorSecondaryContainer))
                setTextColor(M3.color(m3, MaterialR.attr.colorOnSecondaryContainer))
                setOnClickListener { pickWidget() }
            }
        addView(
            scroll.apply {
                addView(
                    content.apply {
                        orientation = LinearLayout.VERTICAL
                        addView(searchBar, LinearLayout.LayoutParams(MATCH, WRAP))
                        addView(date, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(24) })
                        addView(widgets, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
                        addView(
                            edit,
                            LinearLayout.LayoutParams(WRAP, WRAP).apply {
                                gravity = Gravity.CENTER_HORIZONTAL
                                topMargin = dp(8)
                            },
                        )
                    }
                )
            },
            LayoutParams(MATCH, MATCH),
        )
    }

    override fun onOpening() {
        // Widgets fluchten mit den Icon-Spalten des Homescreens
        grid = IconGrid.of(launcher)
        content.setPadding(grid.inset, dp(8), grid.inset, dp(32))
        date.text = DateFormat.format(DateFormat.getBestDateTimePattern(resources.configuration.locales[0], "EEEEdMMMM"), Date())
        host.startListening()
        rebuild()
    }

    override fun onClosed() = host.stopListening()

    override fun onBottomInset(bottom: Int) = scroll.setPadding(0, 0, 0, bottom)

    /** Ergebnis der Widget-Auswahl bzw. -Konfiguration aus [Launcher.onActivityResult]. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_PICK && requestCode != REQUEST_CONFIGURE) return false
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        if (resultCode != Activity.RESULT_OK || id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(id)
            return true
        }
        val info = widgetManager.getAppWidgetInfo(id)
        if (requestCode == REQUEST_PICK && info?.configure != null) {
            pendingWidgetId = id
            host.startAppWidgetConfigureActivityForResult(launcher, id, 0, REQUEST_CONFIGURE, null)
            return true
        }
        saveIds(loadIds() + id)
        rebuild()
        return true
    }

    private fun pickWidget() {
        pendingWidgetId = host.allocateAppWidgetId()
        launcher.startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId),
            REQUEST_PICK,
        )
    }

    /**
     * Widgets in iOS-Größen: klein (zwei Spalten, quadratisch), mittel (alle Spalten) und groß
     * (volle Breite, doppelt hoch), abgeleitet aus der Zielgröße des Widgets. Kleine stehen paarweise.
     */
    private fun rebuild() {
        widgets.removeAllViews()
        // Klein = zwei Spalten breit, mittel/groß = alle Spalten, Abstand wie zwischen Icons
        val columns = launcher.deviceProfile.inv.numColumns
        val gap = grid.gap
        val full = grid.width(columns)
        val square = grid.width(columns / 2)
        var row: LinearLayout? = null
        for (id in loadIds()) {
            val info = widgetManager.getAppWidgetInfo(id) ?: continue
            val wide = info.targetCellWidth > 2 || (info.targetCellWidth == 0 && info.minWidth > square)
            val tall = info.targetCellHeight > 2 || (info.targetCellHeight == 0 && info.minHeight > square)
            val width = if (wide) full else square
            val height = if (tall) square * 2 + gap else square
            val view = host.createView(context, id, info)
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(width / density, height / density)))
            view.setOnLongClickListener {
                confirmRemove(id, info.loadLabel(context.packageManager))
                true
            }
            val card = RoundedFrame(view).apply { background = card() }
            if (wide) {
                row = null
                widgets.addView(card, LinearLayout.LayoutParams(width, height).apply { bottomMargin = gap })
            } else {
                val target =
                    row?.takeIf { it.childCount == 1 }
                        ?: LinearLayout(context).also {
                            row = it
                            widgets.addView(it, LinearLayout.LayoutParams(full, WRAP).apply { bottomMargin = gap })
                        }
                target.addView(
                    card,
                    LinearLayout.LayoutParams(width, height).apply {
                        if (target.childCount == 1) marginStart = gap
                    },
                )
            }
        }
    }

    private fun confirmRemove(id: Int, label: CharSequence) {
        MaterialAlertDialogBuilder(m3)
            .setTitle(context.getString(R.string.today_remove_widget, label))
            .setPositiveButton(R.string.today_remove) { _, _ ->
                host.deleteAppWidgetId(id)
                saveIds(loadIds() - id)
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Rahmen mit abgerundetem Hintergrund, der den Widget-Inhalt beschneidet. */
    private class RoundedFrame(child: View) : android.widget.FrameLayout(child.context) {
        init {
            clipToOutline = true
            addView(child, LayoutParams(MATCH, MATCH))
        }
    }

    private fun loadIds(): List<Int> =
        prefs.getString(KEY_IDS, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    private fun saveIds(ids: List<Int>) =
        prefs.edit().putString(KEY_IDS, ids.distinct().joinToString(",")).apply()

    /** Karte nach Material 3: Tonfläche, extra große Ecken, beschneidet das Widget. */
    private fun card() =
        GradientDrawable().apply {
            cornerRadius = M3.SHAPE_EXTRA_LARGE_DP * density
            setColor(M3.color(m3, MaterialR.attr.colorSurfaceContainer))
        }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val HOST_ID = 0x4C47 // „LG“, getrennt vom Host von Launcher3
        const val PREFS = "letzgo_today"
        const val KEY_IDS = "widget_ids"
        const val REQUEST_PICK = 0x4C01
        const val REQUEST_CONFIGURE = 0x4C02
    }
}
