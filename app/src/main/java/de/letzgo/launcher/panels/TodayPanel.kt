package de.letzgo.launcher.panels

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.letzgo.launcher.ui.M3
import kotlin.math.abs
import com.google.android.material.R as MaterialR

/**
 * Heute-Seite links von der ersten Seite (Logik wie iOS, Gestaltung Material 3): eine Spalte mit
 * Widgets in Karten, direkt auf dem Hintergrundbild wie der Homescreen.
 * Hat einen eigenen [AppWidgetHost]; Widgets kommen über die Widget-Auswahl des Systems dazu. Langes
 * Drücken entfernt sie oder legt sie zu Stapeln zusammen, die sich vertikal durchblättern lassen.
 */
class TodayPanel(launcher: Launcher) : SidePanel(launcher, fromLeft = true) {

    private val m3 = M3.context(launcher)
    private val density = resources.displayMetrics.density
    private val prefs = launcher.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
    private val widgetManager = AppWidgetManager.getInstance(launcher)
    private val host = AppWidgetHost(launcher, HOST_ID)
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val widgets = LinearLayout(m3).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(m3).apply { clipToPadding = false }
    private val content = LinearLayout(m3)
    private var grid = IconGrid.of(launcher)

    init {
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
                        addView(widgets, LinearLayout.LayoutParams(MATCH, WRAP))
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
        // Widgets fluchten mit den Icon-Spalten und der ersten Icon-Reihe des Homescreens
        grid = IconGrid.of(launcher)
        // Erstes Widget beginnt auf Höhe der ersten Icon-Reihe des Homescreens
        val own = IntArray(2).also(::getLocationInWindow)
        val top = (grid.top - own[1] - paddingTop).coerceAtLeast(dp(8))
        content.setPadding(grid.inset, top, grid.inset, dp(32))
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
        saveStacks(loadStacks() + listOf(listOf(id)))
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
     * (volle Breite, doppelt hoch). Ein Stapel nimmt die Größe seines ersten Widgets an; kleine
     * Karten stehen paarweise.
     */
    private fun rebuild() {
        widgets.removeAllViews()
        // Klein = zwei Spalten breit, mittel/groß = alle Spalten, Abstand wie zwischen Icons
        val columns = launcher.deviceProfile.inv.numColumns
        val gap = grid.gap
        val full = grid.width(columns)
        val square = grid.width(columns / 2)
        var row: LinearLayout? = null
        val stacks = loadStacks()
        stacks.forEachIndexed { index, stack ->
            val infos = stack.mapNotNull { id -> widgetManager.getAppWidgetInfo(id)?.let { id to it } }
            if (infos.isEmpty()) return@forEachIndexed
            val first = infos.first().second
            val wide = first.targetCellWidth > 2 || (first.targetCellWidth == 0 && first.minWidth > square)
            val tall = first.targetCellHeight > 2 || (first.targetCellHeight == 0 && first.minHeight > square)
            val width = if (wide) full else square
            val height = if (tall) square * 2 + gap else square
            val views =
                infos.map { (id, info) ->
                    host.createView(context, id, info).apply {
                        updateAppWidgetSize(Bundle(), listOf(SizeF(width / density, height / density)))
                    }
                }
            val card =
                StackCard(
                    views,
                    M3.color(m3, MaterialR.attr.colorOnSurface),
                    M3.color(m3, MaterialR.attr.colorSurfaceContainerHighest, 0.85f),
                ) { current ->
                    showMenu(index, infos[current].first, infos[current].second.loadLabel(context.packageManager))
                }.apply { background = card() }
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

    /**
     * Menü beim langen Drücken: Widget entfernen, mit dem Stapel darüber zusammenlegen (Smart Stack)
     * oder aus seinem Stapel lösen.
     */
    private fun showMenu(stackIndex: Int, id: Int, label: CharSequence) {
        val stacks = loadStacks()
        val inStack = stacks[stackIndex].size > 1
        val actions =
            listOfNotNull(
                if (stackIndex > 0) R.string.today_stack_with_above else null,
                if (inStack) R.string.today_unstack else null,
                R.string.today_remove,
            )
        MaterialAlertDialogBuilder(m3)
            .setTitle(label)
            .setItems(actions.map(context::getString).toTypedArray()) { _, which ->
                when (actions[which]) {
                    R.string.today_stack_with_above -> {
                        val merged = stacks.toMutableList()
                        val moved = merged.removeAt(stackIndex)
                        merged[stackIndex - 1] = merged[stackIndex - 1] + moved
                        saveStacks(merged)
                        rebuild()
                    }
                    R.string.today_unstack -> {
                        val split = stacks.toMutableList()
                        split[stackIndex] = split[stackIndex] - id
                        split.add(stackIndex + 1, listOf(id))
                        saveStacks(split)
                        rebuild()
                    }
                    R.string.today_remove -> confirmRemove(id, label)
                }
            }
            .show()
    }

    private fun confirmRemove(id: Int, label: CharSequence) {
        MaterialAlertDialogBuilder(m3)
            .setTitle(context.getString(R.string.today_remove_widget, label))
            .setPositiveButton(R.string.today_remove) { _, _ ->
                host.deleteAppWidgetId(id)
                saveStacks(loadStacks().map { it - id })
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Karte mit einem oder mehreren Widgets (Stapel). Abgerundet und beschneidend; erkennt langes
     * Drücken und bei Stapeln vertikales Wischen selbst, weil Widgets Berührungen ihrer Kind-Views
     * nicht weiterreichen. Seitenpunkte rechts zeigen die Position im Stapel.
     */
    private class StackCard(
        private val pages: List<View>,
        dotColor: Int,
        pillColor: Int,
        private val onLongPress: (current: Int) -> Unit,
    ) : android.widget.FrameLayout(pages.first().context) {

        private val density = resources.displayMetrics.density
        private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillColor }
        private val pill = RectF()
        private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
        private var current = 0
        private var downX = 0f
        private var downY = 0f
        private var handled = false
        private val longPress = Runnable {
            handled = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress(current)
        }

        init {
            clipToOutline = true
            setWillNotDraw(false)
            pages.forEachIndexed { i, page ->
                page.visibility = if (i == 0) VISIBLE else INVISIBLE
                addView(page, LayoutParams(MATCH, MATCH))
            }
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    handled = false
                    postDelayed(longPress, longPressTimeout)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) removeCallbacks(longPress)
                    // Vertikal wischen blättert im Stapel
                    if (pages.size > 1 && !handled && abs(dy) > touchSlop * 2 && abs(dy) > abs(dx) * 1.5f) {
                        handled = true
                        parent.requestDisallowInterceptTouchEvent(true)
                        show((current + if (dy < 0) 1 else pages.size - 1) % pages.size, up = dy < 0)
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
            }
            // Nach langem Drücken oder Blättern bekommt das Widget den Rest der Geste nicht mehr
            return handled
        }

        override fun onTouchEvent(event: MotionEvent) = handled

        private fun show(index: Int, up: Boolean) {
            if (index == current) return
            val out = pages[current]
            val incoming = pages[index]
            val distance = height.toFloat() * if (up) -1 else 1
            out.animate().translationY(distance).setDuration(PAGE_MS).withEndAction {
                out.visibility = INVISIBLE
                out.translationY = 0f
            }.start()
            incoming.translationY = -distance
            incoming.visibility = VISIBLE
            incoming.animate().translationY(0f).setDuration(PAGE_MS).start()
            current = index
            invalidate()
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (pages.size < 2) return
            val radius = 2.5f * density
            val spacing = 9 * density
            val x = width - 12 * density
            val top = height / 2f - spacing * (pages.size - 1) / 2
            // Kleine Pille hinter den Punkten, damit sie auf jedem Widget sichtbar sind
            val half = 6 * density
            pill.set(x - half, top - half, x + half, top + spacing * (pages.size - 1) + half)
            canvas.drawRoundRect(pill, half, half, pillPaint)
            pages.indices.forEach { i ->
                dotPaint.alpha = if (i == current) 0xE6 else 0x66
                canvas.drawCircle(x, top + i * spacing, radius, dotPaint)
            }
        }

        private companion object {
            const val PAGE_MS = 250L
        }
    }

    /** Stapel aus Widget-IDs; „1,2;3“ = Stapel aus 1 und 2, dann 3 allein. */
    private fun loadStacks(): List<List<Int>> {
        val stored = prefs.getString(KEY_STACKS, null)
            // Aus 0.7: jede ID ein eigener Stapel
            ?: prefs.getString(KEY_IDS, "").orEmpty().replace(',', ';')
        return stored.split(';')
            .map { stack -> stack.split(',').mapNotNull { it.toIntOrNull() } }
            .filter { it.isNotEmpty() }
    }

    private fun saveStacks(stacks: List<List<Int>>) =
        prefs.edit()
            .putString(KEY_STACKS, stacks.filter { it.isNotEmpty() }.joinToString(";") { it.joinToString(",") })
            .remove(KEY_IDS)
            .apply()

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
        const val KEY_STACKS = "widget_stacks"
        const val REQUEST_PICK = 0x4C01
        const val REQUEST_CONFIGURE = 0x4C02
    }
}
