package de.letzgo.launcher.panels

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
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
import de.letzgo.launcher.ui.Backdrop
import de.letzgo.launcher.ui.M3
import de.letzgo.launcher.ui.M3Menu
import kotlin.math.abs
import com.google.android.material.R as MaterialR

/**
 * Heute-Seite links von der ersten Seite (Logik wie iOS, Gestaltung Material 3): eine Spalte mit
 * Widgets in Karten, direkt auf dem Hintergrundbild wie der Homescreen.
 * Hat einen eigenen [AppWidgetHost]; Widgets kommen über eine eigene Auswahl mit Vorschau dazu ([WidgetPicker]). Langes
 * Drücken öffnet ein Kontextmenü: Größe, Stapeln (vertikal durchblättern), Entfernen.
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

    // Wie auf iOS: Hinzufügen nur im Bearbeiten-Zustand (langes Drücken auf eine freie Stelle oder
    // „Bearbeiten“ im Widget-Menü); bei leerer Seite immer
    private var editing = false
    private val addButton =
        MaterialButton(m3).apply {
            setText(R.string.today_add_widget)
            setIconResource(R.drawable.ic_plus)
            backgroundTintList =
                ColorStateList.valueOf(M3.color(m3, MaterialR.attr.colorSecondaryContainer))
            setTextColor(M3.color(m3, MaterialR.attr.colorOnSecondaryContainer))
            iconTint = ColorStateList.valueOf(M3.color(m3, MaterialR.attr.colorOnSecondaryContainer))
            setOnClickListener { pickWidget() }
        }
    private val doneButton =
        MaterialButton(m3).apply {
            setText(R.string.edit_bar_done)
            setOnClickListener { setEditing(false) }
        }
    private val editRow =
        LinearLayout(m3).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(addButton, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(doneButton, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        }

    init {
        addView(
            scroll.apply {
                isFillViewport = true
                addView(
                    content.apply {
                        orientation = LinearLayout.VERTICAL
                        addView(widgets, LinearLayout.LayoutParams(MATCH, WRAP))
                        addView(
                            editRow,
                            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) },
                        )
                        // Langes Drücken auf eine freie Stelle: bearbeiten
                        setOnLongClickListener {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            setEditing(true)
                            true
                        }
                    }
                )
            },
            LayoutParams(MATCH, MATCH),
        )
        updateEditRow()
    }

    private fun setEditing(value: Boolean) {
        editing = value
        updateEditRow()
    }

    private fun updateEditRow() {
        val empty = loadStacks().isEmpty()
        editRow.visibility = if (editing || empty) VISIBLE else GONE
        doneButton.visibility = if (editing) VISIBLE else GONE
    }

    override fun onBack(): Boolean {
        if (!editing) return false
        setEditing(false)
        return true
    }

    override val backdropKey = Backdrop.TODAY

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

    override fun onClosed() {
        host.stopListening()
        setEditing(false)
    }

    override fun onBottomInset(bottom: Int) = scroll.setPadding(0, 0, 0, bottom)

    /** Ergebnis von Bind-Erlaubnis bzw. Widget-Konfiguration aus [Launcher.onActivityResult]. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_BIND && requestCode != REQUEST_CONFIGURE) return false
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        if (resultCode != Activity.RESULT_OK || id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(id)
            return true
        }
        if (requestCode == REQUEST_BIND) configureOrAdd(id) else addWidget(id)
        return true
    }

    /** Eigene Widget-Auswahl mit Vorschau ([WidgetPicker]); gewählt wird sofort gebunden. */
    private fun pickWidget() {
        WidgetPicker.show(m3, widgetManager) { provider -> bind(provider) }
    }

    private fun bind(provider: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingWidgetId = id
        if (widgetManager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null)) {
            configureOrAdd(id)
        } else {
            // Einmalige Erlaubnis des Systems, Widgets zu binden
            launcher.startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile),
                REQUEST_BIND,
            )
        }
    }

    private fun configureOrAdd(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            pendingWidgetId = id
            host.startAppWidgetConfigureActivityForResult(launcher, id, 0, REQUEST_CONFIGURE, null)
        } else {
            addWidget(id)
        }
    }

    private fun addWidget(id: Int) {
        saveStacks(loadStacks() + listOf(listOf(id)))
        rebuild()
    }

    /**
     * Widgets in iOS-Größen: klein (zwei Spalten, quadratisch), mittel (alle Spalten) und groß
     * (volle Breite, doppelt hoch). Ein Stapel nimmt die Größe seines ersten Widgets an; kleine
     * Karten stehen paarweise.
     */
    private fun rebuild() {
        widgets.removeAllViews()
        updateEditRow()
        // Klein = zwei Spalten breit, mittel/groß = alle Spalten, Abstand wie zwischen Icons
        val columns = launcher.deviceProfile.inv.numColumns
        val gap = grid.gap
        val full = grid.fullWidth
        val square = grid.halfWidth
        var row: LinearLayout? = null
        val stacks = loadStacks()
        stacks.forEachIndexed { index, stack ->
            val infos = stack.mapNotNull { id -> widgetManager.getAppWidgetInfo(id)?.let { id to it } }
            if (infos.isEmpty()) return@forEachIndexed
            val size = sizeOf(infos.first().first, infos.first().second, square)
            val wide = size != Size.SMALL
            val width = if (wide) full else square
            val height = if (size == Size.LARGE) square * 2 + gap else square
            val views =
                infos.map { (id, info) ->
                    host.createView(context, id, info).apply {
                        // Ohne Standard-Innenabstand: das Widget füllt seine Form bis zur Kante
                        setPadding(0, 0, 0, 0)
                        updateAppWidgetSize(Bundle(), listOf(SizeF(width / density, height / density)))
                    }
                }
            val card =
                StackCard(
                    views,
                    M3.color(m3, MaterialR.attr.colorOnSurface),
                    M3.color(m3, MaterialR.attr.colorSurfaceContainerHighest, 0.85f),
                ) { card, current ->
                    showMenu(card, index, infos[current].first, infos[current].second.loadLabel(context.packageManager))
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
     * Kontextmenü beim langen Drücken, am Widget verankert: Größe (klein/mittel/groß wie auf iOS),
     * mit dem Stapel darüber zusammenlegen (Smart Stack), aus dem Stapel lösen, entfernen.
     */
    private fun showMenu(anchor: View, stackIndex: Int, id: Int, label: CharSequence) {
        val stacks = loadStacks()
        val stack = stacks[stackIndex]
        val current = widgetManager.getAppWidgetInfo(stack.first())?.let { sizeOf(stack.first(), it, grid.halfWidth) }
        val items =
            Size.entries.map { size ->
                M3Menu.Item(size.icon, size.label, checked = size == current) {
                    setSize(stack.first(), size)
                }
            } +
                listOfNotNull(
                    if (stackIndex > 0) {
                        M3Menu.Item(R.drawable.ic_view_carousel, R.string.today_stack_with_above) {
                            val merged = stacks.toMutableList()
                            val moved = merged.removeAt(stackIndex)
                            merged[stackIndex - 1] = merged[stackIndex - 1] + moved
                            saveStacks(merged)
                            rebuild()
                        }
                    } else {
                        null
                    },
                    if (stack.size > 1) {
                        M3Menu.Item(R.drawable.ic_split_vertical, R.string.today_unstack) {
                            val split = stacks.toMutableList()
                            split[stackIndex] = split[stackIndex] - id
                            split.add(stackIndex + 1, listOf(id))
                            saveStacks(split)
                            rebuild()
                        }
                    } else {
                        null
                    },
                    M3Menu.Item(R.drawable.enter_home_gardening_icon, R.string.today_edit) {
                        setEditing(true)
                    },
                    M3Menu.Item(R.drawable.ic_remove_no_shadow, R.string.today_remove, destructive = true) {
                        confirmRemove(id, label)
                    },
                )
        M3Menu.show(anchor, items)
    }

    /** Widget-Größen wie auf iOS. */
    private enum class Size(val key: String, val label: Int, val icon: Int) {
        SMALL("s", R.string.today_size_small, R.drawable.ic_widget_width_decrease),
        MEDIUM("m", R.string.today_size_medium, R.drawable.ic_widget_width_increase),
        LARGE("l", R.string.today_size_large, R.drawable.ic_widget_height_increase),
    }

    /** Gewählte Größe, sonst aus den Angaben des Widgets ([square] = Breite eines kleinen). */
    private fun sizeOf(id: Int, info: AppWidgetProviderInfo, square: Int): Size {
        Size.entries.firstOrNull { it.key == prefs.getString(KEY_SIZE + id, null) }?.let { return it }
        val wide = info.targetCellWidth > 2 || (info.targetCellWidth == 0 && info.minWidth > square)
        val tall = info.targetCellHeight > 2 || (info.targetCellHeight == 0 && info.minHeight > square)
        return when {
            wide && tall -> Size.LARGE
            wide -> Size.MEDIUM
            else -> Size.SMALL
        }
    }

    private fun setSize(id: Int, size: Size) {
        prefs.edit().putString(KEY_SIZE + id, size.key).apply()
        rebuild()
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
        private val onLongPress: (card: View, current: Int) -> Unit,
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
            onLongPress(this, current)
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

    /**
     * Form der Widgets: extra große M3-Ecken, beschneidet das Widget. Ohne eigene Fläche, denn Widgets
     * bringen ihren Hintergrund selbst mit (wie auf dem Homescreen).
     */
    private fun card() =
        GradientDrawable().apply {
            cornerRadius = M3.SHAPE_EXTRA_LARGE_DP * density
            setColor(Color.TRANSPARENT)
        }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val HOST_ID = 0x4C47 // „LG“, getrennt vom Host von Launcher3
        const val PREFS = "letzgo_today"
        const val KEY_IDS = "widget_ids"
        const val KEY_STACKS = "widget_stacks"
        const val REQUEST_BIND = 0x4C01
        const val KEY_SIZE = "size_"
        const val REQUEST_CONFIGURE = 0x4C02
    }
}
