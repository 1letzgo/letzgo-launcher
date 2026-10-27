package de.letzgo.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.graphics.ColorUtils
import com.android.app.animation.Interpolators
import com.android.launcher3.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.R as MaterialR

/**
 * Design tokens and components of letzgo, the only place that decides how our own UI looks:
 * Material 3 with dynamic colors (from the wallpaper, light/dark like the system). Launcher3 itself
 * has no Material Components theme, so our views and dialogs get a context with the M3 theme
 * ([context]); Launcher3's own folders get the same surface and its context menus the same role
 * (opaque, so text stays readable over translucent tiles) through color overrides in
 * res/color-v31 and res/values-v31.
 *
 * - Colors: [surface] (one translucent tonal surface for everything that lies on the wallpaper),
 *   [onSurface], [onSurfaceVariant], [onWallpaper] for text and icons directly on the wallpaper.
 * - Type: [Type], always through [text].
 * - Shape: [Shape]. Spacing: dp values on the 4 dp grid, converted with [dp].
 * - Motion: [Motion].
 * - Components: [iconButton], [snackbar], [ripple], [choiceAdapter].
 */
object M3 {

    fun context(base: Context): Context =
        ContextThemeWrapper(base, MaterialR.style.Theme_Material3_DynamicColors_DayNight)

    fun color(context: Context, @AttrRes attr: Int): Int =
        MaterialColors.getColor(context, attr, "M3")

    /** M3-Farbe mit Deckkraft [alpha] (0..1). */
    fun color(context: Context, @AttrRes attr: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color(context, attr), (alpha * 255).toInt())

    fun isNight(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    // --- Farben -----------------------------------------------------------------------------

    /**
     * Die eine Fläche auf dem Hintergrundbild: M3-Rolle surfaceContainerHigh, halbtransparent
     * (res/color-v31/letzgo_surface_*). Dock, Ordner, Mediathek-Kacheln, Kategorien, Suchleisten
     * und Seitenkarten. Kontextmenüs: dieselbe Rolle, aber deckend (popup_shade_first).
     */
    fun surface(context: Context): Int =
        context.getColor(if (isNight(context)) R.color.letzgo_surface_dark else R.color.letzgo_surface_light)

    /** Text und Symbole auf [surface]. */
    fun onSurface(context: Context): Int =
        context.getColor(if (isNight(context)) R.color.letzgo_on_surface_dark else R.color.letzgo_on_surface_light)

    /** Zweitrangiger Text, Hinweise und Symbole auf [surface]. */
    fun onSurfaceVariant(context: Context): Int =
        context.getColor(
            if (isNight(context)) R.color.letzgo_on_surface_variant_dark else R.color.letzgo_on_surface_variant_light
        )

    /** [surface] als Fläche mit Eckenradius [cornerDp] (Standard: [Shape.EXTRA_LARGE]). */
    fun surfaceDrawable(context: Context, cornerDp: Float = Shape.EXTRA_LARGE): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = cornerDp * context.resources.displayMetrics.density
            setColor(surface(context))
        }

    /** Hervorhebung von Text und Symbolen direkt auf dem Hintergrundbild (Deckkraft). */
    object Emphasis {
        const val HIGH = 1f
        /** Nicht ausgewählt (Seitenpunkte wie Launcher3), Hinweistext. */
        const val INACTIVE = 0.5f
        /** M3: deaktivierte Inhalte 38 %. */
        const val DISABLED = 0.38f
    }

    /** Text und Symbole direkt auf dem Hintergrundbild: weiß, mit [SHADOW] wie die Icon-Beschriftungen. */
    const val ON_WALLPAPER = Color.WHITE

    /** Schatten für alles, was ohne Fläche auf dem Hintergrundbild liegt (Text, Badges, Griffe). */
    const val SHADOW = 0x66000000

    /** Text direkt auf dem Hintergrundbild (Ordnertitel, Mediathek, Seitenübersicht). */
    fun onWallpaper(text: TextView) {
        text.setTextColor(ON_WALLPAPER)
        text.setHintTextColor(ColorUtils.setAlphaComponent(ON_WALLPAPER, (Emphasis.INACTIVE * 255).toInt()))
        text.setShadowLayer(TEXT_SHADOW_RADIUS * text.resources.displayMetrics.density, 0f, 1f, SHADOW)
    }

    /** Symbol direkt auf dem Hintergrundbild. */
    fun onWallpaper(icon: ImageView) {
        icon.imageTintList = ColorStateList.valueOf(ON_WALLPAPER)
    }

    private const val TEXT_SHADOW_RADIUS = 2f

    // --- Schrift ----------------------------------------------------------------------------

    /** M3-Typskala; nur die Rollen, die letzgo nutzt. */
    enum class Type(@StyleRes val style: Int) {
        /** Titel über geöffneten Ordnern und Kategorien. */
        HEADLINE_MEDIUM(MaterialR.style.TextAppearance_Material3_HeadlineMedium),
        TITLE_MEDIUM(MaterialR.style.TextAppearance_Material3_TitleMedium),
        /** Listenzeilen, Suchfeld. */
        BODY_LARGE(MaterialR.style.TextAppearance_Material3_BodyLarge),
        BODY_MEDIUM(MaterialR.style.TextAppearance_Material3_BodyMedium),
        /** Beschriftungen unter Kacheln und App-Icons (regulär wie die Icon-Beschriftungen von Launcher3). */
        BODY_SMALL(MaterialR.style.TextAppearance_Material3_BodySmall),
        /** Beschriftungen von Aktionen. */
        LABEL_LARGE(MaterialR.style.TextAppearance_Material3_LabelLarge),
        /** Buchstabenleiste. */
        LABEL_SMALL(MaterialR.style.TextAppearance_Material3_LabelSmall),
    }

    /**
     * Setzt Schriftrolle [type] und Farbe [color] (die Schriftrolle allein würde die Farbe des Themes
     * setzen). [color] null: [onWallpaper].
     */
    fun text(view: TextView, type: Type, color: Int?) {
        view.setTextAppearance(type.style)
        if (color == null) onWallpaper(view) else view.setTextColor(color)
    }

    // --- Form, Abstand ----------------------------------------------------------------------

    /** Eckenradien der M3-Formskala in dp; [FULL] = halbe Höhe (Pille, Kreis). */
    object Shape {
        const val EXTRA_SMALL = 4f
        const val SMALL = 8f
        const val MEDIUM = 12f
        const val LARGE = 16f
        const val EXTRA_LARGE = 28f

        /** Volle Rundung für ein Element der Höhe [heightDp]. */
        fun full(heightDp: Int) = heightDp / 2f
    }

    /** Mindestgröße einer Berührungsfläche (M3, Barrierefreiheit). */
    const val TOUCH_TARGET_DP = 48

    // --- Bewegung ---------------------------------------------------------------------------

    /** M3-Dauern und -Kurven. */
    object Motion {
        const val SHORT2 = 100L
        const val SHORT3 = 150L
        const val SHORT4 = 200L
        const val MEDIUM2 = 300L
        const val MEDIUM4 = 400L

        /** Ein- und Ausblenden, Positionswechsel auf dem Bildschirm. */
        val STANDARD: Interpolator = Interpolators.STANDARD
        /** Etwas tritt auf (Öffnen). */
        val EMPHASIZED_DECELERATE: Interpolator = Interpolators.EMPHASIZED_DECELERATE
        /** Etwas verlässt den Bildschirm (Schließen). */
        val EMPHASIZED_ACCELERATE: Interpolator = Interpolators.EMPHASIZED_ACCELERATE
    }

    // --- Komponenten ------------------------------------------------------------------------

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
        val color = color(context(context), MaterialR.attr.colorOnSurface, RIPPLE_ALPHA)
        return RippleDrawable(ColorStateList.valueOf(color), null, mask)
    }

    private const val RIPPLE_ALPHA = 0.12f

    /**
     * M3-Icon-Button (Standard): 48-dp-Berührungsfläche, Zustandsebene. Mit [onWallpaper] liegt er
     * direkt auf dem Hintergrundbild (weißes Symbol), sonst auf [surface].
     */
    fun iconButton(
        context: Context,
        @DrawableRes icon: Int,
        @StringRes label: Int,
        onWallpaper: Boolean,
        onClick: () -> Unit,
    ): MaterialButton =
        MaterialButton(context(context), null, MaterialR.attr.materialIconButtonStyle).apply {
            setIconResource(icon)
            iconTint = ColorStateList.valueOf(if (onWallpaper) ON_WALLPAPER else onSurfaceVariant(context))
            contentDescription = context.getString(label)
            setOnClickListener { onClick() }
        }

    /** Kurze Rückmeldung am unteren Rand (M3-Snackbar statt Toast). */
    fun snackbar(anchor: View, @StringRes text: Int) {
        // Im Inhalt des Fensters statt in Launcher3s DragLayer, die die Ränder verwerfen würde
        val parent = anchor.rootView.findViewById<View>(android.R.id.content) ?: anchor
        val snackbar =
            Snackbar.make(context(anchor.context), parent, anchor.context.getString(text), Snackbar.LENGTH_SHORT)
        // M3-Rand rundum; im Launcher-Kontext kommt er nicht aus dem Stil. Den Abstand zu
        // Navigations- und Gestenleiste ergänzt die Snackbar selbst.
        (snackbar.view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val margin = anchor.context.dp(SNACKBAR_MARGIN_DP)
            lp.setMargins(margin, margin, margin, margin)
            snackbar.view.layoutParams = lp
        }
        snackbar.show()
    }

    private const val SNACKBAR_MARGIN_DP = 8

    /**
     * Einträge für eine Einfachauswahl im M3-Dialog mit sichtbarem Radiobutton (der Standard des
     * Dialogs zeigt in unserem Theme-Kontext keinen).
     */
    fun choiceAdapter(context: Context, labels: List<CharSequence>): ListAdapter =
        object : ArrayAdapter<CharSequence>(context, 0, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? MaterialRadioButton) ?: MaterialRadioButton(context).apply {
                    setPadding(context.dp(12), 0, context.dp(24), 0)
                    minHeight = context.dp(TOUCH_TARGET_DP)
                    isClickable = false
                    isFocusable = false
                    text(this, Type.BODY_LARGE, color(context, MaterialR.attr.colorOnSurface))
                }
                row.text = getItem(position)
                row.isChecked = (parent as? ListView)?.isItemChecked(position) == true
                return row
            }
        }
}

/** dp in Pixel (gerundet); Abstände liegen auf dem 4-dp-Raster. */
fun Context.dp(value: Number): Int = (value.toFloat() * resources.displayMetrics.density + 0.5f).toInt()

/** dp in Pixel ohne Rundung (Radien, Strichstärken). */
fun Context.dpF(value: Number): Float = value.toFloat() * resources.displayMetrics.density

fun View.dp(value: Number): Int = context.dp(value)

fun View.dpF(value: Number): Float = context.dpF(value)
