package de.letzgo.launcher.edit

import android.content.res.ColorStateList
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.CellLayout
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.R
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.popup.PopupData
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.letzgo.launcher.LetzgoLauncher
import de.letzgo.launcher.ui.Backdrop
import de.letzgo.launcher.ui.M3

/**
 * Edit Pages, laid out like the page editor of Samsung's launcher (Material 3 surfaces): the pages
 * as large cards side by side to swipe through, above the centered card a home button (make it the
 * default page, [DefaultPage]) and a trash button (delete it, its apps stay in the App Library),
 * below page dots with a home dot for the default page and "+" to add a page, at the bottom the
 * home actions (wallpaper & style, widgets, settings). Long-press and drag reorders pages, tapping a
 * card or back applies the changes and goes to that page.
 */
class PageOverview(
    private val launcher: Launcher,
    private val onClosed: () -> Unit,
    private val onAddPage: () -> Unit,
    private val actions: () -> List<PopupData>,
) : FrameLayout(launcher), Insettable {

    private data class Page(val screenId: Int, val preview: Picture, val itemCount: Int)

    private val m3 = M3.context(launcher)
    private val density = resources.displayMetrics.density
    private val backCallback = OnBackInvokedCallback { close(apply = true, goTo = current) }
    private val pages = mutableListOf<Page>()
    private var originalOrder = emptyList<Int>()
    // Default page (screen ID); saved when closing
    private var defaultScreen: Int? = null
    private var current = 0
    private val adapter = PageAdapter()
    private val snap = PagerSnapHelper()
    private val list =
        RecyclerView(m3).apply {
            layoutManager = LinearLayoutManager(m3, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@PageOverview.adapter
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) updateCurrent()
                    }
                }
            )
        }
    private val homeButton = toolButton(R.drawable.ic_letzgo_home, R.string.page_overview_default) {
        pages.getOrNull(current)?.let {
            defaultScreen = it.screenId
            updateChrome()
        }
    }
    private val trashButton = toolButton(R.drawable.ic_letzgo_delete, R.string.page_overview_delete) {
        confirmDelete(current)
    }
    private val dots = LinearLayout(m3).apply { gravity = Gravity.CENTER }
    private val bottomBar = LinearLayout(m3).apply { gravity = Gravity.CENTER }
    private var cardWidth = 0
    private var cardHeight = 0

    val isOpen
        get() = parent != null && visibility == VISIBLE

    init {
        isClickable = true
        addView(
            LinearLayout(m3).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    FrameLayout(m3).apply {
                        addView(homeButton, LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
                        addView(trashButton, LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
                    },
                    LinearLayout.LayoutParams(MATCH, dp(64)).apply { topMargin = dp(24) },
                )
                addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
                addView(dots, LinearLayout.LayoutParams(MATCH, dp(40)).apply { topMargin = dp(16) })
                addView(bottomBar, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(24) })
            },
            LayoutParams(MATCH, MATCH),
        )
        snap.attachToRecyclerView(list)
        // Long-press and drag sideways reorders the pages
        ItemTouchHelper(
                object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.START or ItemTouchHelper.END, 0) {
                    override fun onMove(
                        rv: RecyclerView,
                        from: RecyclerView.ViewHolder,
                        to: RecyclerView.ViewHolder,
                    ): Boolean {
                        val page = pages.removeAt(from.bindingAdapterPosition)
                        pages.add(to.bindingAdapterPosition, page)
                        adapter.notifyItemMoved(from.bindingAdapterPosition, to.bindingAdapterPosition)
                        current = to.bindingAdapterPosition
                        updateChrome()
                        return true
                    }

                    override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}

                    // Neighbors are only partly on screen: swap with the next card once the dragged
                    // card has moved a fifth of its width towards it
                    override fun chooseDropTarget(
                        selected: RecyclerView.ViewHolder,
                        dropTargets: MutableList<RecyclerView.ViewHolder>,
                        curX: Int,
                        curY: Int,
                    ): RecyclerView.ViewHolder? {
                        val dx = curX - selected.itemView.left
                        val step = selected.itemView.width / 5
                        val position = selected.bindingAdapterPosition
                        val wanted =
                            when {
                                dx > step -> position + 1
                                dx < -step -> position - 1
                                else -> return null
                            }
                        return dropTargets.firstOrNull { it.bindingAdapterPosition == wanted }
                    }

                    override fun getMoveThreshold(holder: RecyclerView.ViewHolder) = 0.05f


                    // Back onto the centered card after dropping
                    override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
                        super.clearView(rv, holder)
                        rv.post { resnap() }
                    }
                }
            )
            .attachToRecyclerView(list)
    }

    override fun setInsets(insets: Rect) {
        setPadding(insets.left, insets.top, insets.right, insets.bottom + dp(32))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutCards()
    }

    /** Card size from the screen: about three quarters of the width, as tall as the space allows. */
    private fun layoutCards() {
        val ratio = pages.firstOrNull()?.preview?.let { it.height.toFloat() / it.width.coerceAtLeast(1) } ?: 2f
        val available = list.height.takeIf { it > 0 } ?: return
        cardWidth = minOf((width * CARD_WIDTH).toInt(), (available / ratio).toInt())
        cardHeight = (cardWidth * ratio).toInt()
        val side = (width - paddingLeft - paddingRight - cardWidth) / 2
        // Each card has a gap on both sides; the list padding centers the card itself
        list.setPadding(side - dp(CARD_GAP_DP), 0, side - dp(CARD_GAP_DP), 0)
        // Home and trash line up with the edges of the centered card
        (homeButton.parent as View).setPadding(side - dp(12), 0, side - dp(12), 0)
        adapter.notifyDataSetChanged()
    }

    fun open() {
        pages.clear()
        val workspace = launcher.workspace
        val order = workspace.screenOrder
        for (i in 0 until order.size()) {
            val screenId = order[i]
            val page = workspace.getScreenWithId(screenId) ?: continue
            pages += Page(screenId, preview(page), page.shortcutsAndWidgets.childCount)
        }
        originalOrder = pages.map { it.screenId }
        defaultScreen = DefaultPage.screenId(launcher)?.takeIf { it in originalOrder } ?: originalOrder.firstOrNull()
        current = workspace.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        buildBottomBar()
        if (parent == null) {
            launcher.dragLayer.addView(this, LayoutParams(MATCH, MATCH))
        }
        bringToFront()
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(FADE_MS).start()
        setHomeVisible(false)
        post {
            layoutCards()
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(current, 0)
            updateChrome()
        }
        launcher.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            backCallback,
        )
    }

    /**
     * Applies the changes; [goTo] is the page (index in the new order) shown afterwards, null = the
     * default page, [STAY] = leave the workspace alone (Add Page moves to the new page itself).
     */
    private fun close(apply: Boolean, goTo: Int? = null) {
        launcher.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        val newOrder = pages.map { it.screenId }
        if (apply) {
            // Screen IDs are reassigned when reordering (see applyChanges)
            val targetIds = originalOrder.sorted()
            val index = newOrder.indexOf(defaultScreen)
            DefaultPage.setScreenId(launcher, if (index >= 0) targetIds[index] else null)
            if (newOrder != originalOrder) applyChanges(newOrder)
            val page = goTo?.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            if (goTo != STAY) launcher.workspace.postDelayed({
                if (page != null) launcher.workspace.snapToPage(page) else launcher.workspace.moveToDefaultScreen()
            }, RELOAD_DELAY_MS)
        }
        setHomeVisible(true)
        animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            visibility = GONE
            onClosed()
        }.start()
    }

    private fun resnap() {
        val view = snap.findSnapView(list.layoutManager) ?: return
        val distance = snap.calculateDistanceToFinalSnap(list.layoutManager!!, view) ?: return
        list.smoothScrollBy(distance[0], distance[1])
    }

    private fun updateCurrent() {
        val view = snap.findSnapView(list.layoutManager) ?: return
        val position = list.getChildAdapterPosition(view)
        if (position >= 0 && position != current) {
            current = position
            updateChrome()
        }
    }

    /** Home/trash state of the centered page and the page dots. */
    private fun updateChrome() {
        val page = pages.getOrNull(current)
        val isDefault = page != null && page.screenId == defaultScreen
        homeButton.isActivated = isDefault
        homeButton.setImageResource(if (isDefault) R.drawable.ic_letzgo_home else R.drawable.ic_letzgo_home_outline)
        trashButton.isEnabled = pages.size > 1
        trashButton.alpha = if (pages.size > 1) 1f else DISABLED_ALPHA
        dots.removeAllViews()
        pages.forEachIndexed { index, p ->
            val isHome = p.screenId == defaultScreen
            dots.addView(
                ImageView(m3).apply {
                    if (isHome) setImageResource(R.drawable.ic_letzgo_home) else setImageDrawable(dot())
                    imageTintList = ColorStateList.valueOf(ON_WALLPAPER)
                    alpha = if (index == current) 1f else INACTIVE_ALPHA
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setOnClickListener { list.smoothScrollToPosition(index) }
                },
                LinearLayout.LayoutParams(dp(DOT_TOUCH_DP), dp(DOT_TOUCH_DP)).apply { val p2 = dp(DOT_PAD_DP); setPadding(p2, p2, p2, p2) },
            )
        }
        dots.addView(
            ImageView(m3).apply {
                setImageResource(R.drawable.ic_plus)
                imageTintList = ColorStateList.valueOf(ON_WALLPAPER)
                alpha = INACTIVE_ALPHA
                contentDescription = context.getString(R.string.page_overview_add)
                val pad = dp(DOT_PAD_DP)
                setPadding(pad, pad, pad, pad)
                setOnClickListener {
                    close(apply = true, goTo = STAY)
                    onAddPage()
                }
            },
            LinearLayout.LayoutParams(dp(DOT_TOUCH_DP), dp(DOT_TOUCH_DP)),
        )
    }

    private fun dot() =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ON_WALLPAPER)
            setSize(dp(8), dp(8))
        }

    /** Wallpaper & style, widgets, home settings: icon above label, white on the wallpaper. */
    private fun buildBottomBar() {
        bottomBar.removeAllViews()
        for (action in actions()) {
            bottomBar.addView(
                LinearLayout(m3).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    foreground = M3.ripple(m3, cornerRadius = M3.SHAPE_LARGE_DP * density)
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    addView(
                        ImageView(m3).apply {
                            setImageResource(action.iconResId)
                            imageTintList = ColorStateList.valueOf(ON_WALLPAPER)
                        },
                        LinearLayout.LayoutParams(dp(28), dp(28)),
                    )
                    addView(
                        TextView(m3).apply {
                            setText(action.labelResId)
                            M3.onWallpaper(this)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            gravity = Gravity.CENTER
                        },
                        LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) },
                    )
                    setOnClickListener { view ->
                        close(apply = true, goTo = current)
                        launcher.workspace.postDelayed({
                            action.popupAction(launcher, ItemInfo(), view)
                        }, FADE_MS + 50)
                    }
                },
                LinearLayout.LayoutParams(0, WRAP, 1f),
            )
        }
    }

    private fun toolButton(icon: Int, label: Int, onClick: () -> Unit) =
        ImageView(m3).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(ON_WALLPAPER)
            contentDescription = context.getString(label)
            scaleType = ImageView.ScaleType.CENTER
            foreground = M3.ripple(m3, cornerRadius = null)
            setOnClickListener { onClick() }
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
        val page = pages.getOrNull(position) ?: return
        if (pages.size <= 1) return
        val remove = {
            pages.removeAt(position)
            if (page.screenId == defaultScreen) defaultScreen = pages.firstOrNull()?.screenId
            current = current.coerceAtMost(pages.size - 1)
            adapter.notifyItemRemoved(position)
            updateChrome()
        }
        // Empty pages go without asking
        if (page.itemCount == 0) {
            remove()
            return
        }
        MaterialAlertDialogBuilder(m3)
            .setTitle(R.string.page_overview_delete_title)
            .setMessage(R.string.page_overview_delete_message)
            .setPositiveButton(R.string.page_overview_delete) { _, _ -> remove() }
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
            cell.layoutParams = RecyclerView.LayoutParams(cardWidth + 2 * dp(CARD_GAP_DP), MATCH)
            cell.addView(
                ImageView(m3).apply {
                    setImageDrawable(PictureDrawable(page.preview))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    // Card in the widget surface, like folders and the dock
                    background =
                        GradientDrawable().apply {
                            cornerRadius = M3.SHAPE_EXTRA_LARGE_DP * density
                            setColor(M3.folderSheetColor(m3))
                        }
                    clipToOutline = true
                    val pad = dp(12)
                    setPadding(pad, pad, pad, pad)
                    setOnClickListener { close(apply = true, goTo = holder.bindingAdapterPosition) }
                },
                LayoutParams(cardWidth, cardHeight, Gravity.CENTER),
            )
        }
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val FADE_MS = 180L
        const val RELOAD_DELAY_MS = 300L
        const val STAY = -1
        const val CARD_WIDTH = 0.76f
        const val CARD_GAP_DP = 12
        const val DOT_TOUCH_DP = 28
        const val DOT_PAD_DP = 8
        const val INACTIVE_ALPHA = 0.55f
        const val DISABLED_ALPHA = 0.38f
        const val ON_WALLPAPER = 0xFFFFFFFF.toInt()
    }
}
