package de.letzgo.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.android.launcher3.R
import com.google.android.material.R as MaterialR

/**
 * Suchleiste nach Material 3: 56 dp hoch, volle Rundung, Tonfläche, Lupe als führendes Icon.
 * Mit [editable] = false dient sie nur als Einstieg (Tippen öffnet die Suche).
 */
class SearchBar(context: Context, @StringRes hint: Int, editable: Boolean) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val onSurfaceVariant = M3.color(context, MaterialR.attr.colorOnSurfaceVariant)

    val leading =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_letzgo_search)
            imageTintList = ColorStateList.valueOf(onSurfaceVariant)
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
        }

    val field =
        EditText(context).apply {
            setHint(hint)
            setHintTextColor(onSurfaceVariant)
            setTextColor(M3.color(context, MaterialR.attr.colorOnSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSingleLine()
            background = null
            setPadding(0, 0, dp(16), 0)
            if (!editable) {
                isFocusable = false
                isCursorVisible = false
                isLongClickable = false
            }
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        setPadding(dp(4), 0, 0, 0)
        background =
            GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(M3.color(context, MaterialR.attr.colorSurfaceContainerHigh))
            }
        addView(leading, LayoutParams(dp(48), dp(48)))
        addView(field, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    /** Lupe oder Zurück-Pfeil als führendes Icon (M3-Suchansicht). */
    fun showBack(back: Boolean) {
        leading.setImageResource(if (back) R.drawable.ic_letzgo_back else R.drawable.ic_letzgo_search)
    }

    private fun dp(value: Int) = (value * density).toInt()
}
