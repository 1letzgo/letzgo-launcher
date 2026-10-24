package de.letzgo.launcher.panels

import android.content.Context
import android.graphics.Rect
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
import androidx.core.view.doOnPreDraw
import com.android.app.animation.Interpolators
import com.android.launcher3.icons.BitmapInfo.Companion.FLAG_THEMED
import com.android.launcher3.model.data.AppInfo
import de.letzgo.launcher.ui.DragIconView
import de.letzgo.launcher.ui.FolderTitle
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
    // Lage der Kachel in Koordinaten dieser View; beim Öffnen ermittelt (ohne eigene Transformation)
    private var origin: Rect? = null
    private val title =
        TextView(context).apply { FolderTitle.style(this) }
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
                addView(title, LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = FolderTitle.gap(context) })
                addView(sheet, LinearLayout.LayoutParams(WRAP, WRAP))
            },
            LayoutParams(WRAP, WRAP, Gravity.CENTER),
        )
    }

    /** [tile]: Kachel, aus der sich die Kategorie öffnet und in die sie sich wieder schließt. */
    fun show(category: AppCategory, apps: List<AppInfo>, grid: IconGrid, tile: View) {
        this.apps = apps
        this.grid = grid
        title.setText(category.title)
        val columns = COLUMNS
        val padding = dp(SHEET_PADDING_DP)
        list.layoutManager = GridLayoutManager(context, columns)
        list.setPadding(padding, padding, padding, padding)
        list.clipToPadding = false
        // Breite: bündig mit den Icon-Spalten wie die Kacheln der Mediathek, Höhe höchstens 60 %
        val width = grid.width(columns)
        sheet.layoutParams = LinearLayout.LayoutParams(width, WRAP)
        val rows = (apps.size + columns - 1) / columns
        val rowHeight = grid.iconSize + dp(4) + dp(LABEL_HEIGHT_DP) + grid.gap
        val maxHeight = (resources.displayMetrics.heightPixels * 0.6f).toInt()
        list.layoutParams = LayoutParams(MATCH, minOf(rows * rowHeight + 2 * padding, maxHeight))
        adapter.notifyDataSetChanged()
        list.scrollToPosition(0)
        // Wächst aus der Kachel (M3: betonte, abbremsende Kurve). Erst nach dem Layout, damit die
        // Lage der Fläche feststeht.
        animate().cancel()
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        alpha = 0f
        visibility = VISIBLE
        doOnPreDraw {
            // Beide Lagen im Fenster; diese View ist hier noch nicht transformiert
            val own = IntArray(2).also(::getLocationInWindow)
            val pos = IntArray(2).also(tile::getLocationInWindow)
            val left = pos[0] - own[0]
            val top = pos[1] - own[1]
            origin = Rect(left, top, left + tile.width, top + tile.height)
            applyOriginTransform()
            animate()
                .alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
                .setDuration(OPEN_MS)
                .start()
        }
    }

    fun hide() {
        if (!isOpen) return
        // Schrumpft zurück in die Kachel (M3: betonte, beschleunigende Kurve)
        val target = originTransform()
        animate()
            .alpha(0f)
            .scaleX(target[0]).scaleY(target[0]).translationX(target[1]).translationY(target[2])
            .setInterpolator(Interpolators.EMPHASIZED_ACCELERATE)
            .setDuration(CLOSE_MS)
            .withEndAction { visibility = GONE }
            .start()
    }

    private fun applyOriginTransform() {
        val t = originTransform()
        scaleX = t[0]
        scaleY = t[0]
        translationX = t[1]
        translationY = t[2]
    }

    /**
     * Skalierung und Verschiebung, mit der die Ordnerfläche genau über der Kachel liegt
     * (Skalierung um die Mitte dieser View). Alles in Koordinaten dieser View ohne Transformation.
     */
    private fun originTransform(): FloatArray {
        val from = origin ?: return floatArrayOf(0.92f, 0f, 0f)
        val sheetWidth = sheet.width.takeIf { it > 0 } ?: return floatArrayOf(0.92f, 0f, 0f)
        val scale = from.width().toFloat() / sheetWidth
        val sheetCenterX = sheet.left + (sheet.parent as View).left + sheetWidth / 2f
        val sheetCenterY = sheet.top + (sheet.parent as View).top + sheet.height / 2f
        // Mittelpunkt der Fläche nach Skalierung um die View-Mitte
        val scaledX = width / 2f + (sheetCenterX - width / 2f) * scale
        val scaledY = height / 2f + (sheetCenterY - height / 2f) * scale
        return floatArrayOf(scale, from.exactCenterX() - scaledX, from.exactCenterY() - scaledY)
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
                    // Auf der Ordnerfläche: Schriftfarbe der Launcher3-Ordner
                    setTextColor(M3.onFolderColor(context))
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
        const val OPEN_MS = 400L
        const val CLOSE_MS = 200L
    }
}
