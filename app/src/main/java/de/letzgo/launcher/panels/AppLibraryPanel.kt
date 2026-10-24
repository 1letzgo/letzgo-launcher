package de.letzgo.launcher.panels

import android.app.AlertDialog
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
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import com.android.launcher3.util.PackageManagerHelper
import de.letzgo.launcher.model.AllAppsOnHomeScreen
import de.letzgo.launcher.model.HomeScreenRemovals

/**
 * App-Mediathek rechts neben der letzten Homescreen-Seite, wie auf iOS: Apps nach Kategorien in
 * Kacheln, Suchfeld oben; Suche oder Tippen auf einen Kachel-Stapel zeigt eine alphabetische Liste.
 * Über das Kontextmenü kommen vom Homescreen entfernte Apps zurück.
 */
class AppLibraryPanel(launcher: Launcher) : SidePanel(launcher, fromLeft = false) {

    private data class Group(val category: AppCategory, val apps: List<AppInfo>)

    private val density = resources.displayMetrics.density
    private var groups = emptyList<Group>()
    private var allApps = emptyList<AppInfo>()
    private var listFilter: AppCategory? = null

    private val search =
        EditText(launcher).apply {
            setHint(R.string.library_title)
            setHintTextColor(0xB3FFFFFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSingleLine()
            gravity = Gravity.CENTER
            background = glass(12 * density)
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            setOnFocusChangeListener { _, focused -> if (focused) showList(null) }
            addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun afterTextChanged(s: Editable?) = updateList()
                }
            )
        }
    private val cancel =
        TextView(launcher).apply {
            setText(android.R.string.cancel)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(12), dp(10), 0, dp(10))
            visibility = View.GONE
            setOnClickListener { showTiles() }
        }
    private val recycler = RecyclerView(launcher).apply { clipToPadding = false }
    private val tileAdapter = TileAdapter()
    private val listAdapter = ListAdapter()

    init {
        setBackgroundColor(BACKGROUND)
        val header =
            LinearLayout(launcher).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(search, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(cancel)
            }
        addView(
            LinearLayout(launcher).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), 0)
                addView(header)
                addView(recycler, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(16) })
            },
            LayoutParams(MATCH, MATCH),
        )
        showTiles()
    }

    override fun onOpening() {
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
            val result = listOfNotNull(recent.takeIf { it.isNotEmpty() }?.let { Group(AppCategory.RECENT, it) }) +
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

    private fun showTiles() {
        listFilter = null
        search.text.clear()
        search.clearFocus()
        hideKeyboard()
        cancel.visibility = View.GONE
        recycler.layoutManager = GridLayoutManager(context, 2)
        recycler.adapter = tileAdapter
    }

    private fun showList(filter: AppCategory?) {
        listFilter = filter
        cancel.visibility = View.VISIBLE
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = listAdapter
        updateList()
    }

    private fun updateList() {
        val query = search.text.toString().trim().lowercase()
        val source = listFilter?.let { f -> groups.firstOrNull { it.category == f }?.apps } ?: allApps
        listAdapter.items = source.filter { query.isEmpty() || query in it.title.toString().lowercase() }
        listAdapter.notifyDataSetChanged()
    }

    private fun launch(view: View, app: AppInfo) {
        launcher.startActivitySafely(view, app.intent, app)
        close(animate = false)
    }

    /** Kontextmenü: zurück auf den Homescreen (falls entfernt) und App-Info. */
    private fun showMenu(view: View, app: AppInfo): Boolean {
        val removals = HomeScreenRemovals.get(context)
        val key = ComponentKey(app.componentName!!, app.user)
        val removed = removals.removedAt(key) != null
        val actions =
            listOfNotNull(
                if (removed) R.string.library_add_to_home else null,
                R.string.library_app_info,
            )
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.title)
            .setItems(actions.map(context::getString).toTypedArray()) { _, which ->
                when (actions[which]) {
                    R.string.library_add_to_home -> {
                        removals.forget(listOf(key))
                        AllAppsOnHomeScreen.sync(launcher)
                    }
                    R.string.library_app_info ->
                        PackageManagerHelper.startDetailsActivityForInfo(context, app, null, null)
                }
            }
            .show()
        return true
    }

    private fun hideKeyboard() =
        context.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(windowToken, 0)

    private fun appIcon(app: AppInfo, size: Int) =
        ImageView(context).apply {
            setImageDrawable(app.newIcon(context))
            layoutParams = ViewGroup.LayoutParams(size, size)
            contentDescription = app.title
            setOnClickListener { launch(it, app) }
            setOnLongClickListener { showMenu(it, app) }
        }

    private inner class TileAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = groups.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(LinearLayout(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val group = groups[position]
            // Breite aus dem Bildschirm, weil der RecyclerView beim ersten Binden noch nicht gemessen ist
            val tileSize = (resources.displayMetrics.widthPixels - dp(40) - dp(16)) / 2
            val cell = (tileSize - dp(TILE_PADDING) * 2 - dp(TILE_GAP)) / 2
            val tile = holder.itemView as LinearLayout
            tile.removeAllViews()
            tile.orientation = LinearLayout.VERTICAL
            tile.setPadding(dp(4), 0, dp(4), dp(16))
            val box =
                QuadrantLayout(context, dp(TILE_PADDING), dp(TILE_GAP)).apply {
                    background = glass(22 * density)
                }
            val big = if (group.apps.size <= 4) group.apps else group.apps.take(3)
            big.forEach { box.addView(appIcon(it, cell)) }
            if (group.apps.size > 4) {
                // Stapel der übrigen Apps: kleine Icons, Tippen öffnet die ganze Kategorie
                val small = (cell - dp(TILE_GAP)) / 2
                box.addView(
                    QuadrantLayout(context, 0, dp(TILE_GAP)).apply {
                        layoutParams = ViewGroup.LayoutParams(cell, cell)
                        group.apps.drop(3).take(4).forEach { app ->
                            addView(
                                ImageView(context).apply {
                                    setImageDrawable(app.newIcon(context))
                                    layoutParams = ViewGroup.LayoutParams(small, small)
                                }
                            )
                        }
                        setOnClickListener { showList(group.category) }
                    }
                )
            }
            tile.addView(box, LinearLayout.LayoutParams(tileSize, tileSize))
            tile.addView(
                label(context.getString(group.category.title)),
                LinearLayout.LayoutParams(tileSize, WRAP).apply { topMargin = dp(6) },
            )
        }
    }

    private inner class ListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items = emptyList<AppInfo>()

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(LinearLayout(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val app = items[position]
            val row = holder.itemView as LinearLayout
            row.removeAllViews()
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(6), 0, dp(6))
            row.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            row.addView(
                ImageView(context).apply { setImageDrawable(app.newIcon(context)) },
                LinearLayout.LayoutParams(dp(40), dp(40)),
            )
            row.addView(
                TextView(context).apply {
                    text = app.title
                    setTextColor(0xFFFFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setPadding(dp(14), 0, 0, 0)
                },
                LinearLayout.LayoutParams(0, WRAP, 1f),
            )
            row.setOnClickListener { launch(it, app) }
            row.setOnLongClickListener { showMenu(it, app) }
        }
    }

    private fun label(text: String) =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setSingleLine()
        }

    private fun glass(radius: Float) =
        GradientDrawable().apply {
            cornerRadius = radius
            setColor(0x33FFFFFF)
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
        const val BACKGROUND = 0x40000000
        const val RECENT_COUNT = 8
        const val TILE_PADDING = 12
        const val TILE_GAP = 10
    }
}
