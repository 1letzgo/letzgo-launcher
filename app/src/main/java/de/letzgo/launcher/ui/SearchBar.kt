package de.letzgo.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.android.launcher3.R

/**
 * Suchleiste nach Material 3: 56 dp hoch, volle Rundung, Tonfläche, Lupe als führendes Icon.
 * Mit [editable] = false dient sie nur als Einstieg (Tippen öffnet die Suche).
 */
class SearchBar(context: Context, @StringRes hint: Int, editable: Boolean) : LinearLayout(context) {

    private val onSurfaceVariant = M3.onSurfaceVariant(context)

    val leading =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_letzgo_search)
            imageTintList = ColorStateList.valueOf(onSurfaceVariant)
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
        }

    val field =
        EditText(context).apply {
            M3.text(this, M3.Type.BODY_LARGE, M3.onSurface(context))
            setHint(hint)
            setHintTextColor(onSurfaceVariant)
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
        minimumHeight = dp(HEIGHT_DP)
        setPadding(dp(4), 0, 0, 0)
        background = M3.surfaceDrawable(context, M3.Shape.full(HEIGHT_DP))
        addView(leading, LayoutParams(dp(M3.TOUCH_TARGET_DP), dp(M3.TOUCH_TARGET_DP)))
        addView(field, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    /** Lupe oder Zurück-Pfeil als führendes Icon (M3-Suchansicht). */
    fun showBack(back: Boolean) {
        leading.setImageResource(if (back) R.drawable.ic_letzgo_back else R.drawable.ic_letzgo_search)
    }

    private companion object {
        const val HEIGHT_DP = 56
    }
}
