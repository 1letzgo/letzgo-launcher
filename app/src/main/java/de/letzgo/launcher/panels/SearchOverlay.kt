package de.letzgo.launcher.panels

import android.app.SearchManager
import android.content.Intent
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.icons.BitmapInfo.Companion.FLAG_THEMED
import com.android.launcher3.model.data.AppInfo

/**
 * Suche wie Spotlight: Wischen nach unten oder Tippen auf „Suchen“ öffnet sie über dem
 * weichgezeichneten Homescreen. Findet Apps; die letzte Zeile sucht im Web.
 */
class SearchOverlay(private val launcher: Launcher) : FrameLayout(launcher), Insettable {

    private val density = resources.displayMetrics.density
    private val backCallback = OnBackInvokedCallback { close() }
    private var apps = emptyList<AppInfo>()
    private var results = emptyList<AppInfo>()

    private val field =
        EditText(launcher).apply {
            setHint(R.string.search_hint)
            setHintTextColor(0xB3FFFFFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background =
                GradientDrawable().apply {
                    cornerRadius = 100 * density
                    setColor(0x40FFFFFF)
                }
            val h = dp(18)
            val v = dp(11)
            setPadding(h, v, h, v)
            addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                    override fun afterTextChanged(s: Editable?) = update()
                }
            )
            setOnEditorActionListener { _, action, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER
                if (action == EditorInfo.IME_ACTION_SEARCH || enter) {
                    results.firstOrNull()?.let { launch(this, it) } ?: searchWeb()
                    true
                } else {
                    false
                }
            }
        }
    private val adapter = ResultAdapter()
    private val list =
        RecyclerView(launcher).apply {
            layoutManager = LinearLayoutManager(launcher)
            adapter = this@SearchOverlay.adapter
            clipToPadding = false
        }

    val isOpen
        get() = visibility == View.VISIBLE

    /** Reicht hinter Status- und Gestenleiste, die Trefferliste scrollt unter die Gestenleiste. */
    override fun setInsets(insets: Rect) {
        setPadding(insets.left, insets.top, insets.right, 0)
        list.setPadding(0, 0, 0, insets.bottom + dp(16))
    }

    /** Sichtbarkeit hat sich geändert (beim Öffnen bzw. nach dem Schließen). */
    var onVisibilityChanged: (Boolean) -> Unit = {}

    init {
        visibility = View.GONE
        setBackgroundColor(0x59000000)
        addView(
            LinearLayout(launcher).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), 0)
                addView(field)
                addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(16) })
            },
            LayoutParams(MATCH, MATCH),
        )
    }

    fun open() {
        if (parent == null) launcher.dragLayer.addView(this, LayoutParams(MATCH, MATCH))
        bringToFront()
        apps = launcher.appsView.appsStore.apps.sortedBy { it.title.toString().lowercase() }
        field.text.clear()
        update()
        visibility = View.VISIBLE
        onVisibilityChanged(true)
        alpha = 0f
        animate().alpha(1f).setDuration(FADE_MS).setUpdateListener { setBlur(alpha) }.start()
        field.requestFocus()
        context.getSystemService(InputMethodManager::class.java)
            .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
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
        field.clearFocus()
        animate()
            .alpha(0f)
            .setDuration(FADE_MS)
            .setUpdateListener { setBlur(alpha) }
            .withEndAction {
                visibility = View.GONE
                onVisibilityChanged(false)
            }
            .start()
    }

    /** Tippen auf freie Fläche (nicht auf einen Treffer) schließt die Suche. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) close()
        return true
    }

    private fun setBlur(progress: Float) {
        val radius = BLUR_RADIUS * progress
        val effect =
            if (radius < 1f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        launcher.workspace.setRenderEffect(effect)
        launcher.hotseat.setRenderEffect(effect)
    }

    /** Erst Treffer am Wortanfang, dann im Titel enthaltene. */
    private fun update() {
        val query = field.text.toString().trim().lowercase()
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
        val query = field.text.toString().trim()
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
            get() = field.text.isNotBlank()

        override fun getItemCount() = results.size + if (hasQuery) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(LinearLayout(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = holder.itemView as LinearLayout
            row.removeAllViews()
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(6), 0, dp(6))
            row.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            val title = TextView(context).apply {
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setPadding(dp(14), 0, 0, 0)
            }
            if (position < results.size) {
                val app = results[position]
                row.addView(
                    ImageView(context).apply { setImageDrawable(app.newIcon(context, FLAG_THEMED)) },
                    LinearLayout.LayoutParams(dp(44), dp(44)),
                )
                title.text = app.title
                row.setOnClickListener { launch(it, app) }
            } else {
                row.addView(
                    ImageView(context).apply {
                        setImageResource(android.R.drawable.ic_menu_search)
                        setColorFilter(0xFFFFFFFF.toInt())
                    },
                    LinearLayout.LayoutParams(dp(44), dp(44)),
                )
                title.text = context.getString(R.string.search_web, field.text.toString().trim())
                row.setOnClickListener { searchWeb() }
            }
            row.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val FADE_MS = 180L
        const val BLUR_RADIUS = 60f
    }
}
