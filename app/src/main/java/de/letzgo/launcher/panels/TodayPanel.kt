package de.letzgo.launcher.panels

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import java.util.Date

/**
 * Heute-Ansicht links von der ersten Seite, wie auf iOS: Datum und eine Spalte mit Widgets.
 * Hat einen eigenen [AppWidgetHost]; Widgets kommen über die Widget-Auswahl des Systems dazu und
 * lassen sich per langem Drücken entfernen.
 */
class TodayPanel(launcher: Launcher) : SidePanel(launcher, fromLeft = true) {

    private val density = resources.displayMetrics.density
    private val prefs = launcher.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
    private val widgetManager = AppWidgetManager.getInstance(launcher)
    private val host = AppWidgetHost(launcher, HOST_ID)
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val date =
        TextView(launcher).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    private val widgets = LinearLayout(launcher).apply { orientation = LinearLayout.VERTICAL }

    init {
        setBackgroundColor(0x40000000)
        val edit =
            TextView(launcher).apply {
                setText(R.string.today_add_widget)
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                val h = dp(18)
                val v = dp(8)
                setPadding(h, v, h, v)
                background = glass(100 * density)
                setOnClickListener { pickWidget() }
            }
        addView(
            ScrollView(launcher).apply {
                addView(
                    LinearLayout(launcher).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(20), dp(12), dp(20), dp(32))
                        addView(date)
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
        date.text = DateFormat.format(DateFormat.getBestDateTimePattern(resources.configuration.locales[0], "EEEEdMMMM"), Date())
        host.startListening()
        rebuild()
    }

    override fun onClosed() = host.stopListening()

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

    private fun rebuild() {
        widgets.removeAllViews()
        val width = resources.displayMetrics.widthPixels - dp(40)
        for (id in loadIds()) {
            val info = widgetManager.getAppWidgetInfo(id) ?: continue
            val height = (info.minHeight).coerceAtLeast(dp(MIN_WIDGET_HEIGHT_DP))
            val view = host.createView(context, id, info)
            view.updateAppWidgetSize(
                Bundle(),
                listOf(android.util.SizeF(width / density, height / density)),
            )
            view.setOnLongClickListener { confirmRemove(id, info.loadLabel(context.packageManager)); true }
            widgets.addView(
                RoundedFrame(view).apply { background = glass(22 * density) },
                LinearLayout.LayoutParams(width, height).apply { bottomMargin = dp(12) },
            )
        }
    }

    private fun confirmRemove(id: Int, label: CharSequence) {
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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

    private fun glass(radius: Float) =
        GradientDrawable().apply {
            cornerRadius = radius
            setColor(0x33FFFFFF)
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
        const val MIN_WIDGET_HEIGHT_DP = 140
    }
}
