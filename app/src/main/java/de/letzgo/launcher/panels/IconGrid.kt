package de.letzgo.launcher.panels

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Picture
import android.graphics.Rect
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher

/**
 * Lage der Icon-Spalten auf dem Homescreen, damit Widgets und Mediathek-Kacheln wie auf iOS bündig
 * mit den Icons abschließen.
 *
 * @property inset Abstand vom Bildschirmrand bis zur Außenkante der ersten bzw. letzten Spalte
 * @property pitch Abstand von einer Icon-Kante zur gleichen Kante in der nächsten Spalte
 * @property iconSize Kantenlänge eines Icons
 * @property top sichtbare Oberkante der Icons in der ersten Reihe, in Fensterkoordinaten
 */
data class IconGrid(val inset: Int, val pitch: Int, val iconSize: Int, val top: Int) {

    /** Breite über alle Spalten, von der linken Kante der ersten bis zur rechten der letzten. */
    fun width(columns: Int) = (columns - 1) * pitch + iconSize

    /** Lücke zwischen zwei benachbarten Icons. */
    val gap
        get() = pitch - iconSize

    companion object {
        /** Misst das Raster der aktuellen Seite; ohne Seite eine Schätzung aus dem Bildschirm. */
        fun of(launcher: Launcher): IconGrid {
            val iconSize = launcher.deviceProfile.workspaceProfile.iconSizePx
            val columns = launcher.deviceProfile.inv.numColumns
            val page = launcher.workspace.getChildAt(launcher.workspace.currentPage) as? CellLayout
            val screenWidth = launcher.resources.displayMetrics.widthPixels
            if (page == null || page.width == 0 || columns < 2) {
                val inset = (screenWidth * 0.06f).toInt()
                val top = launcher.deviceProfile.insets.top + (48 * launcher.resources.displayMetrics.density).toInt()
                return IconGrid(inset, (screenWidth - 2 * inset - iconSize) / (columns - 1).coerceAtLeast(1), iconSize, top)
            }
            val first = Rect().also { page.cellToRect(0, 0, 1, 1, it) }
            val last = Rect().also { page.cellToRect(columns - 1, 0, 1, 1, it) }
            // Zellen sind relativ zur Seite; die Seite selbst ist zum Bildschirm verschoben
            val pageLeft = page.left - launcher.workspace.scrollX + launcher.workspace.left
            val left = pageLeft + first.left + (first.width() - iconSize) / 2
            val pitch = (last.left - first.left) / (columns - 1)
            // Oberkante: Zelle im Fenster plus Abstand des Icons innerhalb seiner Zelle
            val container = page.shortcutsAndWidgets
            val containerPos = IntArray(2).also(container::getLocationInWindow)
            val top = containerPos[1] + first.top - container.top + iconOffsetInCell(launcher)
            return IconGrid(left, pitch, iconSize, top)
        }

        /**
         * Abstand von der Zellen- zur sichtbaren Icon-Oberkante, gemessen an einem vorhandenen
         * App-Icon: Icon-Grenzen plus durchsichtiger Rand im Icon-Bild. So beginnen Suchleiste und
         * Widgets dort, wo man die Icons sieht.
         */
        private fun iconOffsetInCell(launcher: Launcher): Int {
            val workspace = launcher.workspace
            val bounds = Rect()
            for (i in 0 until workspace.childCount) {
                val container = (workspace.getChildAt(i) as? CellLayout)?.shortcutsAndWidgets ?: continue
                for (j in 0 until container.childCount) {
                    val icon = container.getChildAt(j) as? BubbleTextView ?: continue
                    icon.getIconBounds(bounds)
                    return bounds.top + transparentTop(icon, bounds)
                }
            }
            return 0
        }

        private var cachedInset: Pair<Int, Int>? = null

        /** Durchsichtige Zeilen oben im Icon-Bild (Icons sind Hardware-Bitmaps: über ein Picture kopiert). */
        private fun transparentTop(icon: BubbleTextView, bounds: Rect): Int {
            val size = bounds.height()
            cachedInset?.takeIf { it.first == size }?.let { return it.second }
            val drawable = icon.icon ?: return 0
            val inset =
                try {
                    val picture = Picture()
                    val canvas = picture.beginRecording(size, size)
                    val old = Rect(drawable.bounds)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                    drawable.bounds = old
                    picture.endRecording()
                    val bitmap = Bitmap.createBitmap(picture).copy(Bitmap.Config.ARGB_8888, false)
                    val x = size / 2
                    (0 until size).firstOrNull { y -> Color.alpha(bitmap.getPixel(x, y)) > 128 } ?: 0
                } catch (e: RuntimeException) {
                    0
                }
            cachedInset = size to inset
            return inset
        }
    }
}
