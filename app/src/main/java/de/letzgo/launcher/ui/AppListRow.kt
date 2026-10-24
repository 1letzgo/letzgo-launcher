package de.letzgo.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR

/** Listenzeile nach Material 3: 56 dp, 40-dp-Icon, Text in „body large“. */
class AppListRow(context: Context) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val icon = ImageView(context)
    private val title =
        TextView(context).apply {
            setTextColor(M3.color(context, MaterialR.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSingleLine()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        addView(icon, LayoutParams(dp(40), dp(40)))
        addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
    }

    fun bind(drawable: Drawable?, text: CharSequence?, tintIcon: Boolean = false) {
        icon.setImageDrawable(drawable)
        icon.imageTintList =
            if (tintIcon) {
                android.content.res.ColorStateList.valueOf(M3.color(context, MaterialR.attr.colorOnSurfaceVariant))
            } else {
                null
            }
        icon.scaleType = if (tintIcon) ImageView.ScaleType.CENTER_INSIDE else ImageView.ScaleType.FIT_CENTER
        title.text = text
    }

    private fun dp(value: Int) = (value * density).toInt()
}
