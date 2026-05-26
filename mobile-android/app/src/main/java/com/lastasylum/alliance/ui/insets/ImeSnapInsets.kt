package com.lastasylum.alliance.ui.insets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

/**
 * IME-inset без пошагового следования за анимацией клавиатуры.
 *
 * Системная клавиатура по-прежнему может плавно выезжать (это рисует IME/OEM),
 * но layout приложения переключается сразу на конечное значение в [onStart]/[onEnd],
 * а не на каждом кадре [onProgress] — без лишних recomposition/measure.
 */
@Stable
class ImeSnapState {
    var bottomPx by mutableIntStateOf(0)
        private set

    val isVisible: Boolean
        get() = bottomPx > 0

    internal fun applyBottomPx(px: Int) {
        if (bottomPx != px) bottomPx = px
    }
}

val LocalImeSnapState = compositionLocalOf<ImeSnapState> {
    error("ImeSnapState not provided — wrap root in ProvideImeSnapInsets")
}

@Composable
fun ProvideImeSnapInsets(content: @Composable () -> Unit) {
    val view = LocalView.current
    val state = remember { ImeSnapState() }
    androidx.compose.runtime.DisposableEffect(view) {
        var animating = false
        fun syncImeBottom() {
            val root = ViewCompat.getRootWindowInsets(view) ?: return
            state.applyBottomPx(root.getInsets(WindowInsetsCompat.Type.ime()).bottom)
        }
        val insetsListener = OnApplyWindowInsetsListener { v, insets ->
            if (!animating) {
                state.applyBottomPx(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            }
            ViewCompat.onApplyWindowInsets(v, insets)
        }
        val animCallback = object : WindowInsetsAnimationCompat.Callback(
            WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP,
        ) {
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat = insets

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat,
            ): WindowInsetsAnimationCompat.BoundsCompat {
                animating = true
                // К моменту onStart конечный inset уже известен — сразу поднимаем UI.
                syncImeBottom()
                return bounds
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                animating = false
                syncImeBottom()
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, insetsListener)
        ViewCompat.setWindowInsetsAnimationCallback(view, animCallback)
        syncImeBottom()
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            ViewCompat.setWindowInsetsAnimationCallback(view, null)
        }
    }
    CompositionLocalProvider(LocalImeSnapState provides state, content = content)
}
