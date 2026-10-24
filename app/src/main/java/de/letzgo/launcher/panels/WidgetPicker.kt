package de.letzgo.launcher.panels

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import de.letzgo.launcher.ui.M3
import com.google.android.material.R as MaterialR

/**
 * Widget-Auswahl für die Heute-Seite wie die Widget-Galerie auf iOS: M3-Bottom-Sheet mit einer
 * Vorschau je Widget, nach App sortiert. Die Größe lässt sich danach im Kontextmenü ändern.
 */
object WidgetPicker {

    fun show(m3: Context, manager: AppWidgetManager, onPick: (AppWidgetProviderInfo) -> Unit) {
        val density = m3.resources.displayMetrics.density
        val pm = m3.packageManager
        val providers =
            manager.getInstalledProvidersForProfile(Process.myUserHandle())
                .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                .sortedWith(
                    compareBy<AppWidgetProviderInfo>(
                        { appLabel(m3, it).lowercase() },
                        { it.loadLabel(pm).lowercase() },
                    )
                )
        val dialog = BottomSheetDialog(m3)
        val list =
            RecyclerView(m3).apply {
                layoutManager = LinearLayoutManager(m3)
                clipToPadding = false
                setPadding(0, 0, 0, (24 * density).toInt())
            }
        list.adapter = Adapter(m3, providers) {
            dialog.dismiss()
            onPick(it)
        }
        dialog.setContentView(
            LinearLayout(m3).apply {
                orientation = LinearLayout.VERTICAL
                // M3: Griff oben am Bottom-Sheet
                addView(BottomSheetDragHandleView(m3))
                addView(
                    TextView(m3).apply {
                        setText(R.string.today_choose_widget)
                        setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleLarge)
                        setTextColor(M3.color(m3, MaterialR.attr.colorOnSurface))
                        gravity = Gravity.CENTER
                        val pad = (16 * density).toInt()
                        setPadding(pad, 0, pad, pad)
                    }
                )
                addView(list, LinearLayout.LayoutParams(MATCH, WRAP))
            }
        )
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    private fun appLabel(context: Context, info: AppWidgetProviderInfo) =
        try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
        } catch (e: Exception) {
            info.provider.packageName
        }

    private class Adapter(
        private val m3: Context,
        private val items: List<AppWidgetProviderInfo>,
        private val onPick: (AppWidgetProviderInfo) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val density = m3.resources.displayMetrics.density
        private val previewHeight = (140 * density).toInt()

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(LinearLayout(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val info = items[position]
            val row = holder.itemView as LinearLayout
            row.removeAllViews()
            row.orientation = LinearLayout.VERTICAL
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
            val pad = (16 * density).toInt()
            row.setPadding(pad, pad / 2, pad, pad)
            row.foreground = M3.ripple(m3, cornerRadius = M3.SHAPE_LARGE_DP * density)
            row.setOnClickListener { onPick(info) }

            val preview = FrameLayout(m3)
            row.addView(preview, LinearLayout.LayoutParams(MATCH, previewHeight))
            bindPreview(preview, info, position, holder)

            row.addView(
                TextView(m3).apply {
                    text = info.loadLabel(m3.packageManager)
                    setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleSmall)
                    setTextColor(M3.color(m3, MaterialR.attr.colorOnSurface))
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = pad / 2 },
            )
            row.addView(
                TextView(m3).apply {
                    text = appLabel(m3, info)
                    setTextAppearance(MaterialR.style.TextAppearance_Material3_BodySmall)
                    setTextColor(M3.color(m3, MaterialR.attr.colorOnSurfaceVariant))
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(MATCH, WRAP),
            )
        }

        /**
         * Vorschau: bevorzugt das Vorschau-Layout (Android 12+), sonst das Vorschaubild, sonst das
         * App-Icon. Bilder werden im Hintergrund geladen.
         */
        private fun bindPreview(
            frame: FrameLayout,
            info: AppWidgetProviderInfo,
            position: Int,
            holder: RecyclerView.ViewHolder,
        ) {
            if (info.previewLayout != 0) {
                val view =
                    try {
                        RemoteViews(info.provider.packageName, info.previewLayout).apply(m3, frame)
                    } catch (e: Exception) {
                        null
                    }
                if (view != null) {
                    frame.addView(view, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
                    return
                }
            }
            val image =
                ImageView(m3).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
            frame.addView(image, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER))
            val dpi = m3.resources.displayMetrics.densityDpi
            THREAD_POOL_EXECUTOR.execute {
                val drawable: Drawable? = info.loadPreviewImage(m3, dpi) ?: info.loadIcon(m3, dpi)
                MAIN_EXECUTOR.execute {
                    if (holder.bindingAdapterPosition == position) image.setImageDrawable(drawable)
                }
            }
        }
    }

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
