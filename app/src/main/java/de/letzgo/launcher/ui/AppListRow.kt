package de.letzgo.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Listenzeile nach Material 3: 56 dp, 40-dp-Icon, Text in „body large“. Mit [onWallpaper] liegt die
 * Zeile direkt auf dem Hintergrundbild (weiße Schrift mit Schatten).
 */
class AppListRow(context: Context, onWallpaper: Boolean = false) : LinearLayout(context) {

    val icon = DragIconView(context)
    private val title =
        TextView(context).apply {
            M3.text(this, M3.Type.BODY_LARGE, if (onWallpaper) null else M3.onSurface(context))
            setSingleLine()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(HEIGHT_DP)
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        // Abgerundete Markierung statt des rechteckigen Standard-Ripples
        background = M3.ripple(context, cornerRadius = dpF(M3.Shape.full(HEIGHT_DP)))
        setPadding(dp(12), 0, dp(12), 0)
        addView(icon, LayoutParams(dp(40), dp(40)))
        addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
    }

    fun bind(drawable: Drawable?, text: CharSequence?, tintIcon: Boolean = false) {
        icon.setImageDrawable(drawable)
        icon.imageTintList =
            if (tintIcon) {
                android.content.res.ColorStateList.valueOf(M3.onSurfaceVariant(context))
            } else {
                null
            }
        icon.scaleType = if (tintIcon) ImageView.ScaleType.CENTER_INSIDE else ImageView.ScaleType.FIT_CENTER
        title.text = text
    }

    private companion object {
        const val HEIGHT_DP = 56
    }
}
