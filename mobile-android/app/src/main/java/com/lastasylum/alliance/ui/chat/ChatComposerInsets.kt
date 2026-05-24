package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Якорит панель ввода над IME ([adjustNothing]) с небольшим зазором.
 * В основном приложении контент Scaffold уже над [bottomBar] — не добавляем высоту навбара.
 */
fun Modifier.chatComposerDock(@Suppress("UNUSED_PARAMETER") overlayUi: Boolean): Modifier = composed {
    windowInsetsPadding(WindowInsets.ime)
        .padding(bottom = SquadRelayDimens.composerBottomGap)
}

/** @deprecated Композер в отдельном слое [chatComposerDock]; отступы не на корневом Box. */
fun Modifier.chatComposerHostBottomSpacing(): Modifier = Modifier

/** IME/зазор задаёт [chatComposerDock] на хосте экрана. */
fun Modifier.chatComposerImePadding(overlayUi: Boolean): Modifier = Modifier
