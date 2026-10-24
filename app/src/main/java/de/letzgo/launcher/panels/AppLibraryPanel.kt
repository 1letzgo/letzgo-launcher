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
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapInfo.Companion.FLAG_THEMED
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import com.android.launcher3.util.PackageManagerHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.letzgo.launcher.model.HomeScreenApps
import de.letzgo.launcher.ui.AppListRow
import de.letzgo.launcher.ui.M3
import de.letzgo.launcher.ui.SearchBar
import com.google.android.material.R as MaterialR

/**
 * App-Mediathek als Seite rechts neben der letzten Homescreen-Seite (Logik wie iOS, Gestaltung
 * Material 3): Apps nach Kategorien in Karten, Suchleiste oben; Suche oder Tippen auf einen
 * Kartenstapel zeigt eine alphabetische Liste. Enthält alle Apps; über das Kontextmenü kommen Apps
 * auf den Homescreen, die dort nicht liegen.
 */
class AppLibraryPanel(launcher: Launcher) : SidePanel(launcher, fromLeft = false) {

    private data class Group(val category: AppCategory, val apps: List<AppInfo>)

    private val m3 = M3.context(launcher)
    private val density = resources.displayMetrics.density
    private var groups = emptyList<Group>()
    private var allApps = emptyList<AppInfo>()
    private var listFilter: AppCategory? = null
    private var grid = IconGrid.of(launcher)
    private var listMode = false

    private val searchBar =
        SearchBar(m3, R.string.library_search_hint, editable = true).apply {
            field.setOnFocusChangeListener { _, focused -> if (focused) showList(null) }
            field.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun afterTextChanged(s: Editable?) = updateList()
                }
            )
            leading.setOnClickListener { if (listMode) showTiles() }
        }
    private val listTitle =
        TextView(m3).apply {
            setTextColor(M3.color(m3, MaterialR.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            visibility = View.GONE
        }
    private val recycler = RecyclerView(m3).apply { clipToPadding = false }
    private val content = LinearLayout(m3)
    private val tileAdapter = TileAdapter()
    private val listAdapter = ListAdapter()

    init {
        setBackgroundColor(M3.color(m3, MaterialR.attr.colorSurface))
        addView(
            content.apply {
                orientation = LinearLayout.VERTICAL
                addView(searchBar, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(listTitle, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(20) })
                addView(recycler, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(16) })
            },
            LayoutParams(MATCH, MATCH),
        )
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

    override fun onClosed() = showTiles()

    override fun onBack(): Boolean {
        if (!listMode) return false
        showTiles()
        return true
    }

    override fun onBottomInset(bottom: Int) = recycler.setPadding(0, 0, 0, bottom + dp(16))

    private fun showTiles() {
        listMode = false
        listFilter = null
        searchBar.field.text.clear()
        searchBar.field.clearFocus()
        searchBar.showBack(false)
        listTitle.visibility = View.GONE
        hideKeyboard()
        recycler.layoutManager = GridLayoutManager(context, 2)
        recycler.adapter = tileAdapter
    }

    private fun showList(filter: AppCategory?) {
        listMode = true
        listFilter = filter
        searchBar.showBack(true)
        listTitle.visibility = if (filter != null) View.VISIBLE else View.GONE
        filter?.let { listTitle.setText(it.title) }
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = listAdapter
        updateList()
    }

    private fun updateList() {
        val query = searchBar.field.text.toString().trim().lowercase()
        val source = listFilter?.let { f -> groups.firstOrNull { it.category == f }?.apps } ?: allApps
        listAdapter.items = source.filter { query.isEmpty() || query in it.title.toString().lowercase() }
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
        ImageView(context).apply {
            setImageDrawable(app.newIcon(context, FLAG_THEMED))
            layoutParams = ViewGroup.LayoutParams(size, size)
            contentDescription = app.title
            setOnClickListener { launch(it, app) }
            setOnLongClickListener { showMenu(app) }
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
                        setOnClickListener { showList(group.category) }
                    }
                )
            }
            tile.addView(card, LinearLayout.LayoutParams(tileSize, tileSize).apply { gravity = side })
            tile.addView(
                TextView(m3).apply {
                    setText(group.category.title)
                    gravity = Gravity.CENTER
                    setTextColor(M3.color(m3, MaterialR.attr.colorOnSurfaceVariant))
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
            object : RecyclerView.ViewHolder(AppListRow(m3)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val app = items[position]
            (holder.itemView as AppListRow).apply {
                bind(app.newIcon(context, FLAG_THEMED), app.title)
                setOnClickListener { launch(it, app) }
                setOnLongClickListener { showMenu(app) }
            }
        }
    }

    /** Karte nach Material 3: Tonfläche, extra große Ecken. */
    private fun card() =
        GradientDrawable().apply {
            cornerRadius = M3.SHAPE_EXTRA_LARGE_DP * density
            setColor(M3.color(m3, MaterialR.attr.colorSurfaceContainer))
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
    }
}
