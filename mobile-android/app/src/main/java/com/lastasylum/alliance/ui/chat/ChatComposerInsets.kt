package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Нижний отступ композера: [SquadRelayDimens.composerBottomGap] над клавиатурой / краем контента.
 *
 * В приложении контент чата заканчивается **над** нижним Scaffold-nav — полный [ime] даёт лишний зазор.
 * Передайте [imeObstruction] ≈ высота bottomBar (см. [SquadRelayDimens.chatComposerScaffoldBottomObstruction]).
 *
 * Оверлей: [imeObstruction] = 0 (padding задаётся на корневом [android.widget.FrameLayout]).
 */
fun Modifier.chatComposerDock(imeObstruction: Dp = 0.dp): Modifier = composed {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime
        .asPaddingValues(density)
        .calculateBottomPadding()
    val gap = SquadRelayDimens.composerBottomGap
    val bottom = if (imeBottom > 0.dp) {
        (imeBottom - imeObstruction).coerceAtLeast(0.dp) + gap
    } else {
        gap
    }
    Modifier.padding(bottom = bottom)
}

@Composable
fun rememberChatAppComposerImeObstruction(): Dp = remember {
    SquadRelayDimens.chatComposerScaffoldBottomObstruction
}
