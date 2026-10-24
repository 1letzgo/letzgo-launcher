package de.letzgo.launcher.ui

import android.content.Context
import android.graphics.Rect
import android.widget.ImageView
import com.android.launcher3.dragndrop.DraggableView

/** App-Icon, das Launcher3 ziehen kann (z. B. aus der Mediathek auf den Homescreen). */
class DragIconView(context: Context) : ImageView(context), DraggableView {

    override fun getViewType() = DraggableView.DRAGGABLE_ICON

    override fun getSourceVisualDragBounds(bounds: Rect) = bounds.set(0, 0, width, height)
}
