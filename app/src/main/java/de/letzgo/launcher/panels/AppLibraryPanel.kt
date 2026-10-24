package de.letzgo.launcher.panels

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import de.letzgo.launcher.model.HomeScreenApps
import de.letzgo.launcher.ui.AppListRow
import de.letzgo.launcher.ui.DragIconView
import de.letzgo.launcher.ui.M3
import de.letzgo.launcher.ui.SearchBar
import com.google.android.material.R as MaterialR

/**
 * App-Mediathek als Seite rechts neben der letzten Homescreen-Seite (Logik wie iOS, Gestaltung
 * Material 3), direkt auf dem Hintergrundbild wie der Homescreen: Apps nach Kategorien in Kacheln
 * in Ordnerfarbe, Suchleiste oben. Ein Kategorie-Stapel öffnet sich wie ein Ordner
 * ([CategoryFolder]), die Suche zeigt eine alphabetische Liste. Enthält alle Apps; per Festhalten
 * und Ziehen oder über das Kontextmenü kommen Apps auf den Homescreen.
 */
class AppLibraryPanel(launcher: Launcher) : SidePanel(launcher, fromLeft = false) {

    private data class Group(val category: AppCategory, val apps: List<AppInfo>)

    private val m3 = M3.context(launcher)
    private val density = resources.displayMetrics.density
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
                addView(recycler, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(16) })
            },
            LayoutParams(MATCH, MATCH),
        )
        addView(categoryFolder, LayoutParams(MATCH, MATCH))
        showTiles()
    }

    override fun onOpening() {
        // Karten fluchten mit den Icon-Spalten: jede Karte ist zwei Spalten breit
        grid = IconGrid.of(launcher)
        content.setPadding(grid.inset, dp(8), grid.inset, 0)
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
            val result =
                listOfNotNull(recent.takeIf { it.isNotEmpty() }?.let { Group(AppCategory.RECENT, it) }) +
                    byCategory
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
        content.animate().alpha(0f).setDuration(FADE_MS).start()
    }

    private fun closeCategory() {
        categoryFolder.hide()
        content.animate().alpha(1f).setDuration(FADE_MS).start()
    }

    override fun onBottomInset(bottom: Int) = recycler.setPadding(0, 0, 0, bottom + dp(16))

    private fun showTiles() {
        listMode = false
        searchBar.field.text.clear()
        searchBar.field.clearFocus()
        searchBar.showBack(false)
        hideKeyboard()
        recycler.layoutManager = GridLayoutManager(context, 2)
        recycler.adapter = tileAdapter
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
    }

    private fun launch(view: View, app: AppInfo) {
        launcher.startActivitySafely(view, app.intent, app)
        close(animate = false)
    }

    /** Kontextmenü: auf den Homescreen (falls sie dort nicht liegt) und App-Info. */
    private fun showMenu(app: AppInfo): Boolean {
        HomeScreenApps.isOnHome(launcher, app) { onHome ->
            val actions =
                listOfNotNull(
                    if (onHome) null else R.string.library_add_to_home,
                    R.string.library_app_info,
                )
            MaterialAlertDialogBuilder(m3)
                .setTitle(app.title)
                .setItems(actions.map(context::getString).toTypedArray()) { _, which ->
                    when (actions[which]) {
                        R.string.library_add_to_home -> HomeScreenApps.add(launcher, listOf(app))
                        R.string.library_app_info ->
                            PackageManagerHelper.startDetailsActivityForInfo(context, app, null, null)
                    }
                }
                .show()
        }
        return true
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
                            if (dragStarted) close(animate = true) else showMenu(app)
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
            val tileSize = grid.width(2)
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
                        foreground = M3.ripple(m3, cornerRadius = M3.SHAPE_LARGE_DP * density)
                        setOnClickListener { openCategory(group, card) }
                    }
                )
            }
            tile.addView(card, LinearLayout.LayoutParams(tileSize, tileSize).apply { gravity = side })
            tile.addView(
                TextView(m3).apply {
                    setText(group.category.title)
                    gravity = Gravity.CENTER
                    M3.onWallpaper(this)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
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

    /** Karte in der Farbe der Ordner-Kacheln, extra große M3-Ecken. */
    private fun card() =
        GradientDrawable().apply {
            cornerRadius = M3.SHAPE_EXTRA_LARGE_DP * density
            setColor(M3.folderTileColor(m3))
        }

    private fun dp(value: Int) = (value * density).toInt()

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
        const val TILE_PADDING = 14
        const val TILE_GAP = 10
        const val FADE_MS = 180L
    }
}
