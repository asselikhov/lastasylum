package com.lastasylum.alliance.ui.chat

import androidx.compose.runtime.staticCompositionLocalOf

/** Reverse chat list: compensate scroll when a message expands (keeps bubble top fixed). */
val LocalMessageExpandScrollCompensation = staticCompositionLocalOf<((Int) -> Unit)?> { null }
