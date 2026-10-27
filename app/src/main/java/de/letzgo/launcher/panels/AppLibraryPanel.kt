package de.letzgo.launcher.panels

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.icons.BitmapInfo.Companion.FLAG_THEMED
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import com.android.launcher3.util.PackageManagerHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.letzgo.launcher.model.AppUsage
import de.letzgo.launcher.model.HomeScreenApps
import de.letzgo.launcher.ui.AppListRow
import de.letzgo.launcher.ui.Backdrop
import de.letzgo.launcher.ui.M3Menu
import de.letzgo.launcher.ui.DragIconView
import de.letzgo.launcher.ui.LetterIndex
import de.letzgo.launcher.ui.M3
import de.letzgo.launcher.ui.SearchBar
import de.letzgo.launcher.ui.dp
import de.letzgo.launcher.ui.dpF
import com.google.android.material.R as MaterialR

/**
 * App-Mediathek als Seite rechts neben der letzten Homescreen-Seite (Logik wie iOS, Gestaltung
 * Material 3), direkt auf dem Hintergrundbild wie der Homescreen: Apps nach Kategorien in Kacheln
 * in Ordnerfarbe, Suchleiste oben. Ein Kategorie-Stapel öffnet sich wie ein Ordner
 * ([CategoryFolder]), die Suche (Suchleiste oder Herunterwischen) zeigt eine alphabetische Liste. Enthält alle Apps; per Festhalten
 * und Ziehen oder über das Kontextmenü kommen Apps auf den Homescreen.
 */
class AppLibraryPanel(launcher: Launcher) : SidePanel(launcher, fromLeft = false) {

    private data class Group(val category: AppCategory, val apps: List<AppInfo>)

    private val m3 = M3.context(launcher)
    private var groups = emptyList<Group>()
    private var allApps = emptyList<AppInfo>()
    private var grid = IconGrid.of(launcher)
    private var listMode = false
    private val touchSlop = ViewConfiguration.get(launcher).scaledTouchSlop

    private val searchBar =
        SearchBar(m3, R.string.library_search_hint, editable = true).apply {
            field.setOnFocusChangeListener { _, focused -> if (focused) showList() }
            field.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun afterTextChanged(s: Editable?) = updateList()
                }
            )
            leading.setOnClickListener { if (listMode) showTiles() }
        }
    private val recycler = RecyclerView(m3).apply { clipToPadding = false }
    private val letterIndex = LetterIndex(m3, ::scrollToLetter).apply { visibility = GONE }
    private val content = LinearLayout(m3)
    private val categoryFolder =
        CategoryFolder(
            m3,
            onLaunch = { view, app -> launch(view, app) },
            onDrag = { view, app -> startDrag(view, app) },
            onClose = ::closeCategory,
        )
    private val tileAdapter = TileAdapter()
    private val listAdapter = ListAdapter()

    init {
        addView(
            content.apply {
                orientation = LinearLayout.VERTICAL
                addView(searchBar, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(
                    FrameLayout(m3).apply {
                        addView(recycler, LayoutParams(MATCH, MATCH))
                        addView(letterIndex, LayoutParams(dp(24), MATCH, Gravity.END))
                    },
                    LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(16) },
                )
            },
            LayoutParams(MATCH, MATCH),
        )
        addView(categoryFolder, LayoutParams(MATCH, MATCH))
        showTiles()
    }

    override val backdropKey = Backdrop.LIBRARY

    override fun onOpening() {
        // Karten fluchten mit den Icon-Spalten: zwei Karten füllen die Breite aller Spalten
        grid = IconGrid.of(launcher)
        // Suchleiste beginnt auf Höhe der ersten Icon-Reihe des Homescreens
        val own = IntArray(2).also(::getLocationInWindow)
        val top = (grid.top - own[1] - paddingTop).coerceAtLeast(dp(8))
        content.setPadding(grid.inset, top, grid.inset, 0)
        loadGroups()
    }

    /** Apps nach Kategorien sortieren (auch nach „Kategorie ändern“). */
    private fun loadGroups() {
        val apps = launcher.appsView.appsStore.apps.toList()
        val pm = context.packageManager
        THREAD_POOL_EXECUTOR.execute {
            val infos = apps.associateWith { app -> app.componentName?.packageName?.let { appInfo(pm, it) } }
            val recent =
                apps.filter { it.user == Process.myUserHandle() }
                    .sortedByDescending { installTime(pm, it) }
                    .take(RECENT_COUNT)
            val byCategory =
                apps.groupBy { AppCategories.categorize(it, infos[it]) }
                    .map { (category, list) -> Group(category, list.sortedBy(::sortKey)) }
                    .sortedWith(
                        compareBy<Group> { it.category == AppCategory.OTHER }
                            .thenByDescending { it.apps.size }
                    )
            // Wie auf iOS: zuerst Vorschläge (meistgenutzt), dann zuletzt hinzugefügt
            val suggested = AppUsage.suggestions(context, apps, SUGGESTION_COUNT)
            val result =
                listOfNotNull(
                    suggested.takeIf { it.isNotEmpty() }?.let { Group(AppCategory.SUGGESTIONS, it) },
                    recent.takeIf { it.isNotEmpty() }?.let { Group(AppCategory.RECENT, it) },
                ) + byCategory
            MAIN_EXECUTOR.execute {
                allApps = apps.sortedBy(::sortKey)
                groups = result
                tileAdapter.notifyDataSetChanged()
                updateList()
            }
        }
    }

    override fun onClosed() {
        closeCategory()
        showTiles()
    }

    /** Wie auf iOS: Herunterwischen in der Mediathek öffnet die Suche (nur ganz oben in den Karten). */
    override fun onSwipeDown(): Boolean {
        if (listMode || categoryFolder.isOpen || recycler.canScrollVertically(-1)) return false
        searchBar.field.requestFocus()
        context.getSystemService(InputMethodManager::class.java)
            .showSoftInput(searchBar.field, InputMethodManager.SHOW_IMPLICIT)
        return true
    }

    override fun onBack(): Boolean {
        when {
            categoryFolder.isOpen -> closeCategory()
            listMode -> showTiles()
            else -> return false
        }
        return true
    }

    /** Kategorie wie einen Ordner öffnen; die Mediathek tritt dahinter zurück (wie der Homescreen). */
    private fun openCategory(group: Group, tile: View) {
        categoryFolder.show(group.category, group.apps, grid, tile)
        content.animate().alpha(0f).setDuration(FADE_MS).setInterpolator(M3.Motion.STANDARD).start()
    }

    private fun closeCategory() {
        categoryFolder.hide()
        content.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(M3.Motion.STANDARD).start()
    }

    private var navBottom = 0
    private var imeBottom = 0

    override fun onBottomInset(bottom: Int) {
        navBottom = bottom
        applyBottom()
    }

    /**
     * Mit offener Tastatur enden Liste und Buchstabenleiste über der Tastatur (der Launcher selbst
     * verkleinert sich dafür nicht).
     */
    private fun applyBottom() {
        val bottom = maxOf(navBottom, imeBottom)
        recycler.setPadding(0, 0, 0, bottom + dp(16))
        (letterIndex.layoutParams as? LayoutParams)?.let {
            val margin = bottom + dp(8)
            if (it.bottomMargin != margin) {
                it.bottomMargin = margin
                letterIndex.layoutParams = it
            }
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
        if (ime != imeBottom) {
            imeBottom = ime
            applyBottom()
        }
        return super.onApplyWindowInsets(insets)
    }

    private fun showTiles() {
        listMode = false
        searchBar.field.text.clear()
        searchBar.field.clearFocus()
        searchBar.showBack(false)
        hideKeyboard()
        recycler.layoutManager = GridLayoutManager(context, 2)
        recycler.adapter = tileAdapter
        letterIndex.visibility = GONE
    }

    /** Suche: alphabetische Liste aller Apps, gefiltert nach dem Suchtext. */
    private fun showList() {
        listMode = true
        searchBar.showBack(true)
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = listAdapter
        updateList()
    }

    private fun updateList() {
        val query = searchBar.field.text.toString().trim().lowercase()
        listAdapter.items = allApps.filter { query.isEmpty() || query in it.title.toString().lowercase() }
        listAdapter.notifyDataSetChanged()
        // Buchstabenleiste nur für die ganze Liste, nicht für Suchergebnisse
        letterIndex.setLetters(listAdapter.items.map(::letterOf).distinct())
        letterIndex.visibility = if (listMode && query.isEmpty()) VISIBLE else GONE
    }

    private fun letterOf(app: AppInfo): Char =
        app.title.toString().trim().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

    private fun scrollToLetter(letter: Char) {
        val index = listAdapter.items.indexOfFirst { letterOf(it) == letter }
        if (index >= 0) (recycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
    }

    private fun launch(view: View, app: AppInfo) {
        launcher.startActivitySafely(view, app.intent, app)
        close(animate = false)
    }

    /** Kontextmenü an der App: auf den Homescreen (falls sie dort nicht liegt) und App-Info. */
    private fun showMenu(anchor: View, app: AppInfo): Boolean {
        HomeScreenApps.isOnHome(launcher, app) { onHome ->
            val items =
                listOfNotNull(
                    if (onHome) {
                        null
                    } else {
                        M3Menu.Item(R.drawable.ic_desktop_add, R.string.library_add_to_home) {
                            HomeScreenApps.add(launcher, listOf(app))
                        }
                    },
                    M3Menu.Item(R.drawable.ic_create_new_folder, R.string.library_change_category) {
                        chooseCategory(app)
                    },
                    M3Menu.Item(R.drawable.ic_info_no_shadow, R.string.library_app_info) {
                        PackageManagerHelper.startDetailsActivityForInfo(context, app, null, null)
                    },
                )
            M3Menu.show(anchor, items)
        }
        return true
    }

    /** Eigene Kategorie wählen (M3-Auswahl); „Automatisch“ nimmt die eigene Wahl zurück. */
    private fun chooseCategory(app: AppInfo) {
        val pkg = app.componentName?.packageName ?: return
        val choices = AppCategories.choosable
        val current = AppCategories.override(context, pkg)
        val labels =
            listOf(context.getString(R.string.library_category_auto)) + choices.map { context.getString(it.title) }
        MaterialAlertDialogBuilder(m3)
            .setTitle(app.title)
            .setSingleChoiceItems(M3.choiceAdapter(m3, labels), current?.let { choices.indexOf(it) + 1 } ?: 0) { dialog, which ->
                AppCategories.setOverride(context, pkg, if (which == 0) null else choices[which - 1])
                dialog.dismiss()
                if (categoryFolder.isOpen) closeCategory()
                loadGroups()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Was hinter einem Kontextmenü unscharf wird: die einzelnen Icons und Texte (nicht die Kacheln
     * selbst, sonst würde das gedrückte Icon mit unscharf).
     */
    fun blurTargets(): List<View> {
        val views = mutableListOf<View>()
        fun collect(view: View) {
            if (view.visibility != VISIBLE) return
            if (view is ViewGroup && view !is DragIconView && view.childCount > 0) {
                for (i in 0 until view.childCount) collect(view.getChildAt(i))
            } else {
                views += view
            }
        }
        collect(content)
        return views
    }

    private fun hideKeyboard() =
        context.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(windowToken, 0)

    private fun appIcon(app: AppInfo, size: Int) =
        DragIconView(context).apply {
            setImageDrawable(app.newIcon(context, FLAG_THEMED))
            layoutParams = ViewGroup.LayoutParams(size, size)
            contentDescription = app.title
            tag = app
            foreground = M3.ripple(m3, cornerRadius = null)
            setOnClickListener { launch(it, app) }
            setOnLongClickListener { startDrag(it, app) }
        }

    /**
     * Langes Drücken hebt die App an: Wird sie gezogen, gleitet die Mediathek weg und die App lässt
     * sich auf dem Homescreen ablegen; wird sie nur gehalten, öffnet sich das Kontextmenü.
     */
    private fun startDrag(view: View, app: AppInfo): Boolean {
        view.tag = app
        val options =
            DragOptions().apply {
                preDragCondition =
                    object : DragOptions.PreDragCondition {
                        override fun shouldStartDrag(distanceDragged: Double) = distanceDragged > touchSlop

                        override fun onPreDragStart(dragObject: DropTarget.DragObject) {}

                        override fun onPreDragEnd(dragObject: DropTarget.DragObject, dragStarted: Boolean) {
                            if (dragStarted) close(animate = true) else showMenu(view, app)
                        }
                    }
            }
        launcher.workspace.beginDragShared(view, launcher.appsView, options)
        return true
    }

    private inner class TileAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = groups.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(LinearLayout(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val group = groups[position]
            val tileSize = grid.halfWidth
            val cell = (tileSize - dp(TILE_PADDING) * 2 - dp(TILE_GAP)) / 2
            val side = if (position % 2 == 0) Gravity.START else Gravity.END
            val tile = holder.itemView as LinearLayout
            tile.removeAllViews()
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = side
            tile.setPadding(0, 0, 0, grid.gap)
            tile.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            val card =
                QuadrantLayout(context, dp(TILE_PADDING), dp(TILE_GAP)).apply { background = card() }
            val big = if (group.apps.size <= 4) group.apps else group.apps.take(3)
            big.forEach { card.addView(appIcon(it, cell)) }
            if (group.apps.size > 4) {
                // Stapel der übrigen Apps: kleine Icons, Tippen öffnet die ganze Kategorie
                val small = (cell - dp(TILE_GAP)) / 2
                card.addView(
                    QuadrantLayout(context, 0, dp(TILE_GAP)).apply {
                        layoutParams = ViewGroup.LayoutParams(cell, cell)
                        group.apps.drop(3).take(4).forEach { app ->
                            addView(
                                ImageView(context).apply {
                                    setImageDrawable(app.newIcon(context, FLAG_THEMED))
                                    layoutParams = ViewGroup.LayoutParams(small, small)
                                }
                            )
                        }
                        foreground = M3.ripple(m3, cornerRadius = dpF(M3.Shape.LARGE))
                        setOnClickListener { openCategory(group, card) }
                    }
                )
            }
            tile.addView(card, LinearLayout.LayoutParams(tileSize, tileSize).apply { gravity = side })
            tile.addView(
                TextView(m3).apply {
                    setText(group.category.title)
                    gravity = Gravity.CENTER
                    M3.text(this, M3.Type.BODY_SMALL, null)
                    setSingleLine()
                },
                LinearLayout.LayoutParams(tileSize, WRAP).apply {
                    topMargin = dp(8)
                    gravity = side
                },
            )
        }
    }

    private inner class ListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items = emptyList<AppInfo>()

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(AppListRow(m3, onWallpaper = true)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val app = items[position]
            (holder.itemView as AppListRow).apply {
                bind(app.newIcon(context, FLAG_THEMED), app.title)
                icon.tag = app
                setOnClickListener { launch(it, app) }
                setOnLongClickListener { startDrag(icon, app) }
            }
        }
    }

    /** Karte auf der letzgo-Fläche, extra große M3-Ecken. */
    private fun card() = M3.surfaceDrawable(m3)

    private fun sortKey(app: AppInfo) = app.title.toString().lowercase()

    private fun appInfo(pm: PackageManager, pkg: String) =
        try {
            pm.getApplicationInfo(pkg, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    private fun installTime(pm: PackageManager, app: AppInfo) =
        try {
            pm.getPackageInfo(app.componentName!!.packageName, 0).firstInstallTime
        } catch (e: PackageManager.NameNotFoundException) {
            0L
        }

    /** Ordnet bis zu vier Kinder in einem 2 x 2-Raster an. */
    private class QuadrantLayout(context: Context, private val padding: Int, private val gap: Int) :
        ViewGroup(context) {

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(widthMeasureSpec)
            val cell = (size - 2 * padding - gap) / 2
            val spec = MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY)
            for (i in 0 until childCount) getChildAt(i).measure(spec, spec)
            setMeasuredDimension(size, size)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val cell = (r - l - 2 * padding - gap) / 2
            for (i in 0 until childCount.coerceAtMost(4)) {
                val x = padding + (i % 2) * (cell + gap)
                val y = padding + (i / 2) * (cell + gap)
                getChildAt(i).layout(x, y, x + cell, y + cell)
            }
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val RECENT_COUNT = 8
        const val SUGGESTION_COUNT = 4
        const val TILE_PADDING = 12
        const val TILE_GAP = 12
        const val FADE_MS = M3.Motion.SHORT4
    }
}
