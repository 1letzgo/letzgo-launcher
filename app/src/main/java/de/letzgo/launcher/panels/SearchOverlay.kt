package de.letzgo.launcher.panels

import android.app.SearchManager
import android.content.Intent
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.app.animation.Interpolators
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapInfo.Companion.FLAG_THEMED
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.SystemUiController
import de.letzgo.launcher.ui.AppListRow
import de.letzgo.launcher.ui.M3
import de.letzgo.launcher.ui.SearchBar
import com.google.android.material.R as MaterialR

/**
 * Suche als Material-3-Suchansicht über dem Homescreen; geöffnet über die Suchleiste der Heute-Seite.
 * Findet Apps, die letzte Zeile sucht im Web.
 */
class SearchOverlay(private val launcher: Launcher) : FrameLayout(launcher), Insettable {

    private val m3 = M3.darkContext(launcher)
    private val density = resources.displayMetrics.density
    private val backCallback = OnBackInvokedCallback { close() }
    private var apps = emptyList<AppInfo>()
    private var results = emptyList<AppInfo>()

    private val searchBar =
        SearchBar(m3, R.string.search_hint, editable = true).apply {
            showBack(true)
            leading.setOnClickListener { close() }
            field.imeOptions = EditorInfo.IME_ACTION_SEARCH
            field.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun afterTextChanged(s: Editable?) = update()
                }
            )
            field.setOnEditorActionListener { view, action, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER
                if (action == EditorInfo.IME_ACTION_SEARCH || enter) {
                    results.firstOrNull()?.let { launch(view, it) } ?: searchWeb()
                    true
                } else {
                    false
                }
            }
        }
    private val adapter = ResultAdapter()
    private val list =
        RecyclerView(m3).apply {
            layoutManager = LinearLayoutManager(m3)
            adapter = this@SearchOverlay.adapter
            clipToPadding = false
        }

    val isOpen
        get() = visibility == View.VISIBLE

    init {
        visibility = View.GONE
        isClickable = true
        setBackgroundColor(M3.color(m3, MaterialR.attr.colorSurface))
        addView(
            LinearLayout(m3).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), 0)
                addView(searchBar, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(8) })
            },
            LayoutParams(MATCH, MATCH),
        )
    }

    /** Reicht hinter Status- und Gestenleiste, die Trefferliste scrollt unter die Gestenleiste. */
    override fun setInsets(insets: Rect) {
        setPadding(insets.left, insets.top, insets.right, 0)
        list.setPadding(0, 0, 0, insets.bottom + dp(16))
    }

    fun open() {
        if (parent == null) launcher.dragLayer.addView(this, LayoutParams(MATCH, MATCH))
        bringToFront()
        apps = launcher.appsView.appsStore.apps.sortedBy { it.title.toString().lowercase() }
        searchBar.field.text.clear()
        update()
        visibility = View.VISIBLE
        M3.setSystemBarsForSurface(launcher, onSurface = true, slot = BARS_SLOT)
        // Gleitet leicht von oben herein (M3: betonte, abbremsende Kurve)
        alpha = 0f
        translationY = -dp(SLIDE_DP).toFloat()
        animate().alpha(1f).translationY(0f)
            .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
            .setDuration(OPEN_MS)
            .start()
        searchBar.field.requestFocus()
        context.getSystemService(InputMethodManager::class.java)
            .showSoftInput(searchBar.field, InputMethodManager.SHOW_IMPLICIT)
        launcher.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            backCallback,
        )
    }

    fun close() {
        if (!isOpen) return
        launcher.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        context.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(windowToken, 0)
        searchBar.field.clearFocus()
        M3.setSystemBarsForSurface(launcher, onSurface = false, slot = BARS_SLOT)
        animate().alpha(0f).translationY(-dp(SLIDE_DP).toFloat())
            .setInterpolator(Interpolators.EMPHASIZED_ACCELERATE)
            .setDuration(CLOSE_MS)
            .withEndAction { visibility = View.GONE }
            .start()
    }

    /** Erst Treffer am Wortanfang, dann im Titel enthaltene. */
    private fun update() {
        val query = searchBar.field.text.toString().trim().lowercase()
        results =
            if (query.isEmpty()) {
                emptyList()
            } else {
                val (prefix, other) =
                    apps.filter { query in it.title.toString().lowercase() }
                        .partition { app ->
                            app.title.toString().lowercase().split(' ').any { it.startsWith(query) }
                        }
                prefix + other
            }
        adapter.notifyDataSetChanged()
    }

    private fun launch(view: View, app: AppInfo) {
        launcher.startActivitySafely(view, app.intent, app)
        close()
    }

    private fun searchWeb() {
        val query = searchBar.field.text.toString().trim()
        if (query.isEmpty()) return
        launcher.startActivity(
            Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        close()
    }

    private inner class ResultAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val hasQuery
            get() = searchBar.field.text.isNotBlank()

        override fun getItemCount() = results.size + if (hasQuery) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(AppListRow(m3)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = holder.itemView as AppListRow
            if (position < results.size) {
                val app = results[position]
                row.bind(app.newIcon(context, FLAG_THEMED), app.title)
                row.setOnClickListener { launch(it, app) }
            } else {
                val query = searchBar.field.text.toString().trim()
                row.bind(
                    context.getDrawable(R.drawable.ic_letzgo_search),
                    context.getString(R.string.search_web, query),
                    tintIcon = true,
                )
                row.setOnClickListener { searchWeb() }
            }
        }
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val OPEN_MS = 300L
        const val CLOSE_MS = 150L
        const val SLIDE_DP = 24
        // Eigener Platz, damit die darunterliegende Heute-Seite ihre Leisten behält
        const val BARS_SLOT = SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET
    }
}
