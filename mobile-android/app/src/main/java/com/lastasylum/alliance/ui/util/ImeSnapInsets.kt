package com.lastasylum.alliance.ui.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Убирает «плавное» появление IME-insets: при первой же доле прогресса анимации подставляются
 * финальные границы клавиатуры, так что Compose ([WindowInsets.ime]) не тянет отступ композера.
 */
object ImeSnapInsets {
    fun install(view: View) {
        ViewCompat.setWindowInsetsAnimationCallback(
            view,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE,
            ) {
                private val boundsByAnim =
                    mutableMapOf<Int, WindowInsetsAnimationCompat.BoundsCompat>()

                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat,
                ): WindowInsetsAnimationCompat.BoundsCompat {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        boundsByAnim[System.identityHashCode(animation)] = bounds
                    }
                    return bounds
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat {
                    var out = insets
                    for (anim in runningAnimations) {
                        if (anim.typeMask and WindowInsetsCompat.Type.ime() == 0) continue
                        val b = boundsByAnim[System.identityHashCode(anim)] ?: continue
                        val lower = b.lowerBound
                        val upper = b.upperBound
                        val snapped = if (anim.interpolatedFraction > 0f) upper else lower
                        out = WindowInsetsCompat.Builder(out)
                            .setInsets(WindowInsetsCompat.Type.ime(), snapped)
                            .build()
                    }
                    return out
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    boundsByAnim.remove(System.identityHashCode(animation))
                }
            },
        )
    }
}
