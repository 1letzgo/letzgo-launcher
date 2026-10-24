package de.letzgo.launcher.edit

import android.graphics.Picture
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PictureDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.CellLayout
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.R
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.letzgo.launcher.LetzgoLauncher
import de.letzgo.launcher.ui.Backdrop
import de.letzgo.launcher.ui.M3

/**
 * Seitenübersicht im Bearbeitungsmodus (wie auf iOS über die Seitenpunkte): alle Homescreen-Seiten
 * als Vorschau; Ziehen sortiert sie um, das Minus löscht eine Seite (ihre Apps bleiben in der
 * App-Mediathek). „Fertig“ übernimmt die Änderungen ins Modell und lädt den Homescreen neu.
 */
class PageOverview(private val launcher: Launcher, private val onClosed: () -> Unit) :
    FrameLayout(launcher), Insettable {

    private data class Page(val screenId: Int, val preview: Picture)

    private val m3 = M3.context(launcher)
    private val density = resources.displayMetrics.density
    private val backCallback = OnBackInvokedCallback { close(apply = false) }
    private val pages = mutableListOf<Page>()
    private var originalOrder = emptyList<Int>()
    private val adapter = PageAdapter()
    private val list =
        RecyclerView(m3).apply {
            layoutManager = GridLayoutManager(m3, 2)
            adapter = this@PageOverview.adapter
            clipToPadding = false
        }

    val isOpen
        get() = parent != null && visibility == VISIBLE

    init {
        isClickable = true
        val done =
            MaterialButton(m3).apply {
                setText(R.string.edit_bar_done)
                setOnClickListener { close(apply = true) }
            }
        val title =
            TextView(m3).apply {
                setText(R.string.page_overview_title)
                // Direkt auf dem abgedunkelten Hintergrundbild wie Ordner und Mediathek
                M3.onWallpaper(this)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            }
        addView(
            LinearLayout(m3).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), 0)
                addView(
                    FrameLayout(m3).apply {
                        addView(title, LayoutParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL))
                        addView(done, LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL))
                    },
                    LinearLayout.LayoutParams(MATCH, WRAP),
                )
                addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(16) })
            },
            LayoutParams(MATCH, MATCH),
        )
        // Langes Drücken und Ziehen sortiert die Seiten um
        ItemTouchHelper(
                object :
                    ItemTouchHelper.SimpleCallback(
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END,
                        0,
                    ) {
                    override fun onMove(
                        rv: RecyclerView,
                        from: RecyclerView.ViewHolder,
                        to: RecyclerView.ViewHolder,
                    ): Boolean {
                        val page = pages.removeAt(from.bindingAdapterPosition)
                        pages.add(to.bindingAdapterPosition, page)
                        adapter.notifyItemMoved(from.bindingAdapterPosition, to.bindingAdapterPosition)
                        return true
                    }

                    override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
                }
            )
            .attachToRecyclerView(list)
    }

    override fun setInsets(insets: Rect) {
        setPadding(insets.left, insets.top, insets.right, 0)
        list.setPadding(0, 0, 0, insets.bottom + dp(16))
    }

    fun open() {
        pages.clear()
        val workspace = launcher.workspace
        val order = workspace.screenOrder
        for (i in 0 until order.size()) {
            val screenId = order[i]
            val page = workspace.getScreenWithId(screenId) ?: continue
            pages += Page(screenId, preview(page))
        }
        originalOrder = pages.map { it.screenId }
        adapter.notifyDataSetChanged()
        if (parent == null) {
            launcher.dragLayer.addView(this, LayoutParams(MATCH, MATCH))
        }
        bringToFront()
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(FADE_MS).start()
        setHomeVisible(false)
        launcher.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            backCallback,
        )
    }

    private fun close(apply: Boolean) {
        launcher.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        val newOrder = pages.map { it.screenId }
        if (apply && newOrder != originalOrder) applyChanges(newOrder)
        setHomeVisible(true)
        animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            visibility = GONE
            onClosed()
        }.start()
    }

    /** Homescreen, Dock und Seitenpunkte treten hinter der Übersicht zurück. */
    private fun setHomeVisible(visible: Boolean) {
        val alpha = if (visible) 1f else 0f
        (launcher as? LetzgoLauncher)?.backdrop?.set(Backdrop.OVERVIEW, 1f - alpha)
        listOf(launcher.workspace, launcher.hotseat, launcher.workspace.pageIndicator as View).forEach {
            it.animate().alpha(alpha).setDuration(FADE_MS).start()
        }
    }

    /**
     * Launcher3 zeigt die Seiten aufsteigend nach Seiten-ID. Umsortieren vergibt die bisherigen IDs
     * in neuer Reihenfolge an die Elemente; Elemente gelöschter Seiten kommen vom Homescreen (Apps
     * bleiben in der Mediathek, Widget-IDs werden freigegeben).
     */
    private fun applyChanges(newOrder: List<Int>) {
        val targetIds = originalOrder.sorted()
        val mapping = newOrder.mapIndexed { index, screenId -> screenId to targetIds[index] }.toMap()
        val deleted = originalOrder.toSet() - newOrder.toSet()
        val holder = launcher.appWidgetHolder
        launcher.model.enqueueModelUpdateTask { controller, dataModel, _ ->
            val writer = controller.getModelWriter()
            val onDesktop = synchronized(dataModel) {
                dataModel.itemsIdMap.filter { it.container == CONTAINER_DESKTOP }
            }
            for (item in onDesktop) {
                when {
                    item.screenId in deleted -> delete(writer, item, holder)
                    mapping[item.screenId] != item.screenId ->
                        writer.moveItemInDatabase(
                            item,
                            CONTAINER_DESKTOP,
                            mapping.getValue(item.screenId),
                            item.cellX,
                            item.cellY,
                        )
                }
            }
            MAIN_EXECUTOR.execute { launcher.model.forceReload("letzgo: Seiten geändert") }
        }
    }

    private fun delete(
        writer: com.android.launcher3.model.IModelWriter,
        item: ItemInfo,
        holder: com.android.launcher3.widget.LauncherWidgetHolder?,
    ) {
        when (item) {
            is CollectionInfo -> writer.deleteCollectionAndContentsFromDatabase(item)
            is LauncherAppWidgetInfo -> writer.deleteWidgetInfo(item, holder, "letzgo: Seite gelöscht")
            else -> writer.deleteItemFromDatabase(item, "letzgo: Seite gelöscht")
        }
    }

    private fun confirmDelete(position: Int) {
        MaterialAlertDialogBuilder(m3)
            .setTitle(R.string.page_overview_delete_title)
            .setMessage(R.string.page_overview_delete_message)
            .setPositiveButton(R.string.page_overview_delete) { _, _ ->
                pages.removeAt(position)
                adapter.notifyItemRemoved(position)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Vorschau einer Seite als aufgezeichnetes [Picture]; der Bearbeitungsmodus ist dabei beendet,
     * also ohne Wackeln und Minus. Kein Bitmap, weil die Icons Hardware-Bitmaps sind, die sich nur
     * hardwarebeschleunigt zeichnen lassen.
     */
    private fun preview(page: CellLayout): Picture {
        val picture = Picture()
        val canvas = picture.beginRecording(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1))
        val container = page.shortcutsAndWidgets
        canvas.translate(container.left.toFloat(), container.top.toFloat())
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            canvas.save()
            canvas.translate(child.left.toFloat(), child.top.toFloat())
            child.draw(canvas)
            canvas.restore()
        }
        picture.endRecording()
        return picture
    }

    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(FrameLayout(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val page = pages[position]
            val cell = holder.itemView as FrameLayout
            cell.removeAllViews()
            cell.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            cell.setPadding(dp(8), dp(8), dp(8), dp(8))
            val ratio = page.preview.height.toFloat() / page.preview.width.coerceAtLeast(1)
            val width = (resources.displayMetrics.widthPixels - dp(32)) / 2 - dp(16)
            cell.addView(
                ImageView(m3).apply {
                    setImageDrawable(PictureDrawable(page.preview))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    background =
                        GradientDrawable().apply {
                            cornerRadius = M3.SHAPE_LARGE_DP * density
                            setColor(M3.folderTileColor(m3))
                        }
                    val pad = dp(6)
                    setPadding(pad, pad, pad, pad)
                },
                LayoutParams(width, (width * ratio).toInt()),
            )
            cell.addView(
                ImageView(m3).apply {
                    setImageDrawable(MinusBadge(m3, dp(24).toFloat()).apply { setCenter(dp(12).toFloat(), dp(12).toFloat()) })
                    contentDescription = context.getString(R.string.page_overview_delete)
                    setOnClickListener { confirmDelete(holder.bindingAdapterPosition) }
                },
                LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.START),
            )
        }
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val FADE_MS = 180L
    }
}
