package de.letzgo.launcher.ui

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView

import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.folder.Folder
import com.android.launcher3.views.BaseDragLayer

/**
 * Name eines geöffneten Ordners wie auf iOS groß über dem Ordner, auf dem Hintergrundbild. Antippen
 * benennt um. Gleiche Gestaltung wie der Titel einer geöffneten Mediathek-Kategorie ([style]).
 */
class FolderTitle(private val launcher: Launcher, private val onClosing: () -> Unit) {

    private var folder: Folder? = null
    private var lastState = Folder.STATE_CLOSED
    private val stateListener = Folder.OnFolderStateChangedListener { state -> onFolderState(state) }
    private val view =
        EditText(launcher).apply {
            style(this)
            background = null
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setHint(R.string.folder_hint_text)
            setHintTextColor(0xB3FFFFFF.toInt())
            isFocusableInTouchMode = true
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) commit()
                action == EditorInfo.IME_ACTION_DONE
            }
            setOnFocusChangeListener { _, focused -> if (!focused) commit() }
            alpha = 0f
        }

    /** Ordner wurde geöffnet (null: keiner offen). */
    fun bind(open: Folder?) {
        if (open === folder) return
        folder?.removeOnFolderStateChangedListener(stateListener)
        folder?.letzgoTitleView = null
        folder = open
        if (open == null) {
            hide()
            return
        }
        lastState = Folder.STATE_CLOSED
        open.addOnFolderStateChangedListener(stateListener)
        open.letzgoTitleView = view
        if (view.parent == null) {
            val lp = BaseDragLayer.LayoutParams(MATCH, WRAP).apply { ignoreInsets = true }
            launcher.dragLayer.addView(view, lp)
        }
        view.bringToFront()
        view.setText(open.info.title)
        view.visibility = View.VISIBLE
        place(open)
        view.animate().alpha(1f).setDuration(FADE_MS).setStartDelay(FADE_MS / 2).start()
    }

    /** Über die Ordnerfläche; deren Lage steht beim Öffnen schon in den LayoutParams. */
    private fun place(folder: Folder) {
        val folderTop = (folder.layoutParams as? BaseDragLayer.LayoutParams)?.y ?: folder.top
        view.measure(
            View.MeasureSpec.makeMeasureSpec(launcher.dragLayer.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.translationY = (folderTop - gap(launcher) - view.measuredHeight).toFloat()
    }

    private fun onFolderState(state: Int) {
        // Schließen beginnt mit OFFEN -> ANIMIEREND (isOpen ist dann noch true); ohne Animation
        // direkt GESCHLOSSEN. Der Titel blendet sofort aus, nicht erst nach der Animation.
        val closing = state == Folder.STATE_CLOSED || (state == Folder.STATE_ANIMATING && lastState == Folder.STATE_OPEN)
        lastState = state
        if (closing) {
            hide()
            onClosing()
        }
    }

    private fun hide() {
        if (view.hasFocus()) {
            commit()
            view.clearFocus()
        }
        view.animate().alpha(0f).setStartDelay(0).setDuration(CLOSE_FADE_MS).withEndAction {
            view.visibility = View.GONE
        }.start()
    }

    private fun commit() {
        val f = folder ?: return
        val text = view.text.toString()
        if (text != (f.info.title?.toString() ?: "")) f.applyTitle(text)
        launcher.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    companion object {
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
        private const val FADE_MS = 150L
        private const val CLOSE_FADE_MS = 100L
        private const val TITLE_SP = 28f
        private const val GAP_DP = 20

        /** Titel über einem geöffneten Ordner oder einer Kategorie. */
        fun style(text: TextView) {
            M3.onWallpaper(text)
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP)
            text.gravity = Gravity.CENTER
            text.paintFlags = text.paintFlags or Paint.SUBPIXEL_TEXT_FLAG
        }

        /** Abstand zwischen Titel und Ordnerfläche. */
        fun gap(context: Context) = (GAP_DP * context.resources.displayMetrics.density).toInt()

        /** Platz, den der Titel über der Ordnerfläche braucht (zum Zentrieren beider zusammen). */
        fun spaceAbove(context: Context): Int {
            val text = TextView(context).also(::style)
            return text.paint.fontMetricsInt.let { it.descent - it.ascent } + gap(context)
        }
    }
}
