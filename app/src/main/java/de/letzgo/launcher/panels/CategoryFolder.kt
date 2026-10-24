package de.letzgo.launcher.panels

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.icons.BitmapInfo.Companion.FLAG_THEMED
import com.android.launcher3.model.data.AppInfo
import de.letzgo.launcher.ui.DragIconView
import de.letzgo.launcher.ui.M3

/**
 * Geöffnete Kategorie der App-Mediathek wie ein geöffneter Ordner (iOS): Titel über einer Fläche in
 * Ordnerfarbe mit allen Apps im Raster der Icon-Spalten. Tippen startet, Festhalten und Ziehen legt
 * eine App auf den Homescreen; Tippen daneben oder Zurück schließt.
 */
class CategoryFolder(
    context: Context,
    private val onLaunch: (View, AppInfo) -> Unit,
    private val onDrag: (View, AppInfo) -> Boolean,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private var apps = emptyList<AppInfo>()
    private var grid: IconGrid? = null
    private val title =
        TextView(context).apply {
            M3.onWallpaper(this)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
        }
    private val adapter = AppAdapter()
    private val list =
        RecyclerView(context).apply {
            adapter = this@CategoryFolder.adapter
            overScrollMode = OVER_SCROLL_NEVER
        }
    private val sheet =
        FrameLayout(context).apply {
            background =
                GradientDrawable().apply {
                    cornerRadius = M3.SHAPE_EXTRA_LARGE_DP * density
                    setColor(M3.folderSheetColor(context))
                }
            isClickable = true
            addView(list, LayoutParams(MATCH, WRAP))
        }

    val isOpen
        get() = visibility == VISIBLE

    init {
        visibility = GONE
        // Tippen neben den Ordner schließt ihn
        setOnClickListener { onClose() }
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(title, LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(20) })
                addView(sheet, LinearLayout.LayoutParams(WRAP, WRAP))
            },
            LayoutParams(WRAP, WRAP, Gravity.CENTER),
        )
    }

    fun show(category: AppCategory, apps: List<AppInfo>, grid: IconGrid) {
        this.apps = apps
        this.grid = grid
        title.setText(category.title)
        val columns = COLUMNS
        val padding = dp(SHEET_PADDING_DP)
        list.layoutManager = GridLayoutManager(context, columns)
        list.setPadding(padding, padding, padding, padding)
        list.clipToPadding = false
        // Breite: vier Icon-Spalten wie auf dem Homescreen plus schmaler Rand, Höhe höchstens 60 %
        val width = grid.width(columns) + 2 * padding
        sheet.layoutParams = LinearLayout.LayoutParams(width, WRAP)
        val rows = (apps.size + columns - 1) / columns
        val rowHeight = grid.iconSize + dp(4) + dp(LABEL_HEIGHT_DP) + grid.gap
        val maxHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
        list.layoutParams = LayoutParams(MATCH, minOf(rows * rowHeight + 2 * padding, maxHeight))
        adapter.notifyDataSetChanged()
        list.scrollToPosition(0)
        visibility = VISIBLE
        alpha = 0f
        scaleX = 0.92f
        scaleY = 0.92f
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(FADE_MS).start()
    }

    fun hide() {
        if (!isOpen) return
        animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(FADE_MS)
            .withEndAction { visibility = GONE }
            .start()
    }

    private inner class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = apps.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(LinearLayout(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val app = apps[position]
            val grid = grid ?: return
            val cell = holder.itemView as LinearLayout
            cell.removeAllViews()
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply { bottomMargin = grid.gap }
            val icon =
                DragIconView(context).apply {
                    setImageDrawable(app.newIcon(context, FLAG_THEMED))
                    contentDescription = app.title
                    tag = app
                    foreground = M3.ripple(context, cornerRadius = null)
                    setOnClickListener { onLaunch(it, app) }
                    setOnLongClickListener { onDrag(it, app) }
                }
            cell.addView(icon, LinearLayout.LayoutParams(grid.iconSize, grid.iconSize))
            cell.addView(
                TextView(context).apply {
                    text = app.title
                    M3.onWallpaper(this)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setSingleLine()
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(MATCH, dp(LABEL_HEIGHT_DP)).apply { topMargin = dp(4) },
            )
        }
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val COLUMNS = 4
        const val SHEET_PADDING_DP = 16
        const val LABEL_HEIGHT_DP = 18
        const val FADE_MS = 180L
    }
}
