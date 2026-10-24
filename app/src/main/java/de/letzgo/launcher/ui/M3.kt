package de.letzgo.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.graphics.ColorUtils
import com.android.launcher3.Launcher
import com.android.launcher3.R
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

    /**
     * Farbe einer Ordner-Kachel auf dem Homescreen (Launcher3-Ordnerfarbe, halbtransparent, hell oder
     * dunkel nach Systemmodus). Dock, Mediathek-Kacheln und Suchleisten nutzen sie, damit alles gleich
     * wirkt.
     */
    fun folderTileColor(context: Context): Int =
        context.getColor(if (isNight(context)) R.color.folder_preview_dark else R.color.folder_preview_light)

    /** Farbe eines geöffneten Ordners (halbtransparent), auch für geöffnete Kategorien. */
    fun folderSheetColor(context: Context): Int =
        context.getColor(
            if (isNight(context)) R.color.folder_background_dark else R.color.folder_background_light
        )

    /** Text und Symbole auf den Ordnerfarben. */
    fun onFolderColor(context: Context): Int =
        context.getColor(
            if (isNight(context)) R.color.folder_text_color_dark else R.color.folder_text_color_light
        )

    fun isNight(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * Text direkt auf dem Hintergrundbild (Heute-Seite, Mediathek): weiß mit Schatten wie die
     * Icon-Beschriftungen des Homescreens, damit er auf hellen und dunklen Bildern lesbar ist.
     */
    fun onWallpaper(text: TextView) {
        text.setTextColor(Color.WHITE)
        text.setShadowLayer(WALLPAPER_SHADOW_RADIUS, 0f, 1f, WALLPAPER_SHADOW_COLOR)
    }

    private const val WALLPAPER_SHADOW_RADIUS = 4f
    private const val WALLPAPER_SHADOW_COLOR = 0x8A000000.toInt()

    /**
     * Systemleisten-Symbole passend zu einer deckenden M3-Fläche (hell oder dunkel nach Systemmodus). [slot] ist ein Zustand im
     * [SystemUiController]; der höchste gesetzte gewinnt. Seiten nutzen den Platz des App-Drawers,
     * den letzgo nicht hat, die Suche darüber den des Widget-Blatts.
     */
    fun setSystemBarsForSurface(
        launcher: Launcher,
        onSurface: Boolean,
        slot: Int = SystemUiController.UI_STATE_ALL_APPS,
    ) {
        val flags =
            when {
                !onSurface -> 0
                isNight(launcher) -> SystemUiController.FLAG_DARK_NAV or SystemUiController.FLAG_DARK_STATUS
                else -> SystemUiController.FLAG_LIGHT_NAV or SystemUiController.FLAG_LIGHT_STATUS
            }
        launcher.systemUiController?.updateUiState(slot, flags)
    }

    /** Eckenradien der M3-Formskala in dp. */
    const val SHAPE_LARGE_DP = 16f
    const val SHAPE_EXTRA_LARGE_DP = 28f
}
