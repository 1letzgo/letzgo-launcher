package de.letzgo.launcher.ui

import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import com.android.launcher3.Launcher

/**
 * Hinter einem geöffneten Ordner wird der Homescreen wie auf iOS weichgezeichnet und abgedunkelt,
 * damit der transparente Ordner lesbar bleibt.
 */
class FolderBackdrop(private val launcher: Launcher) {

    private var target = 0f
    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun setVisible(visible: Boolean) {
        val goal = if (visible) 1f else 0f
        if (goal == target) return
        target = goal
        animator?.cancel()
        animator =
            ValueAnimator.ofFloat(progress, goal).apply {
                duration = DURATION_MS
                addUpdateListener { apply(it.animatedValue as Float) }
                start()
            }
    }

    private fun apply(value: Float) {
        progress = value
        val radius = BLUR_RADIUS * value
        val effect =
            if (radius < 1f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        launcher.workspace.setRenderEffect(effect)
        launcher.hotseat.setRenderEffect(effect)
        launcher.workspace.alpha = 1 - DIM * value
        launcher.hotseat.alpha = 1 - DIM * value
        launcher.window?.setBackgroundBlurRadius(radius.toInt())
    }

    private companion object {
        const val DURATION_MS = 200L
        const val BLUR_RADIUS = 50f
        const val DIM = 0.35f
    }
}
