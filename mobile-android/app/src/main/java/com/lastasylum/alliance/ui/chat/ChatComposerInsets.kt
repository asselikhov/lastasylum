package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Оверлей: IME поднимает корень [android.widget.FrameLayout] в [CombatOverlayService];
 * здесь только зазор 8dp над нижним краем контента.
 */
fun Modifier.chatComposerOverlayDock(): Modifier =
    padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение (adjustNothing, bottomBar всегда виден): подъём композера над IME
 * с вычитанием высоты слота bottomBar — иначе [imePadding] даёт лишний зазор ≈ высоте навбара.
 * Только на [ChatComposerBar] / композере форума, не на весь экран.
 */
@Composable
fun Modifier.chatComposerImePadding(): Modifier {
    val density = LocalDensity.current
    val imePx = WindowInsets.ime.getBottom(density)
    if (imePx <= 0) return this
    val obstructionPx = with(density) {
        SquadRelayDimens.chatComposerScaffoldBottomObstruction.roundToPx()
    }
    val bottomPx = (imePx - obstructionPx).coerceAtLeast(0)
    return padding(bottom = with(density) { bottomPx.toDp() })
}

/** @deprecated Используйте [chatComposerImePadding] на [ChatComposerBar], не на весь экран. */
@Composable
fun Modifier.chatScreenImeLift(): Modifier = chatComposerImePadding()

/** @deprecated IME на уровне композера — [chatComposerImePadding] + [chatComposerOverlayDock]. */
@Composable
fun Modifier.chatComposerAppDock(): Modifier =
    chatComposerImePadding().chatComposerOverlayDock()

/** @deprecated Используйте [chatComposerOverlayDock] или [chatScreenImeLift]. */
@Composable
fun Modifier.chatComposerDock(): Modifier = chatComposerOverlayDock()
