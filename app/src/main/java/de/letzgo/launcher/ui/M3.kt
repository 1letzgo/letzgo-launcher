package de.letzgo.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ContextThemeWrapper
import androidx.annotation.AttrRes
import androidx.core.graphics.ColorUtils
import com.android.launcher3.Launcher
import com.android.launcher3.util.SystemUiController
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR

/**
 * Material 3 für eigene Oberflächen: Launcher3 selbst hat kein Material-Components-Theme, deshalb
 * bekommen unsere Views und Dialoge einen Kontext mit dem dynamischen M3-Theme (Farben aus dem
 * Hintergrundbild, hell/dunkel nach System).
 */
object M3 {

    fun context(base: Context): Context =
        ContextThemeWrapper(base, MaterialR.style.Theme_Material3_DynamicColors_DayNight)

    /**
     * Immer das dunkle M3-Schema (Material You), unabhängig vom Systemmodus: für Flächen über dem
     * Hintergrundbild wie App-Mediathek, Heute-Seite und Suche.
     */
    fun darkContext(base: Context): Context =
        ContextThemeWrapper(base, MaterialR.style.Theme_Material3_DynamicColors_Dark)

    fun color(context: Context, @AttrRes attr: Int): Int =
        MaterialColors.getColor(context, attr, "M3")

    /** M3-Farbe mit Deckkraft [alpha] (0..1), damit das Hintergrundbild durchscheint. */
    fun color(context: Context, @AttrRes attr: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color(context, attr), (alpha * 255).toInt())

    /**
     * Markierung beim Antippen (Ripple) in M3-Farbe, auf eine Form begrenzt: [cornerRadius] in px,
     * oder null für einen Kreis (Icons).
     */
    fun ripple(context: Context, cornerRadius: Float?): RippleDrawable {
        val mask =
            GradientDrawable().apply {
                setColor(Color.WHITE)
                if (cornerRadius == null) shape = GradientDrawable.OVAL else this.cornerRadius = cornerRadius
            }
        val color = color(context, MaterialR.attr.colorOnSurface, RIPPLE_ALPHA)
        return RippleDrawable(ColorStateList.valueOf(color), null, mask)
    }

    private const val RIPPLE_ALPHA = 0.12f

    /** Deckkraft halbtransparenter Flächen: Seiten (Mediathek, Heute) und Dock. */
    const val PAGE_ALPHA = 0.8f
    const val DOCK_ALPHA = 0.7f

    /**
     * Helle Systemleisten-Symbole über den dunklen Flächen ([darkContext]). [slot] ist ein Zustand im
     * [SystemUiController]; der höchste gesetzte gewinnt. Seiten nutzen den Platz des App-Drawers,
     * den letzgo nicht hat, die Suche darüber den des Widget-Blatts.
     */
    fun setSystemBarsForSurface(
        launcher: Launcher,
        onSurface: Boolean,
        slot: Int = SystemUiController.UI_STATE_ALL_APPS,
    ) {
        val flags =
            if (onSurface) SystemUiController.FLAG_DARK_NAV or SystemUiController.FLAG_DARK_STATUS else 0
        launcher.systemUiController?.updateUiState(slot, flags)
    }

    /** Eckenradien der M3-Formskala in dp. */
    const val SHAPE_LARGE_DP = 16f
    const val SHAPE_EXTRA_LARGE_DP = 28f
}
