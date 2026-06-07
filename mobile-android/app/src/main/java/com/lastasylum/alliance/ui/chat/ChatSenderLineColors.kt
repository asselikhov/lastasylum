package com.lastasylum.alliance.ui.chat

import androidx.compose.ui.graphics.toArgb
import com.lastasylum.alliance.ui.theme.ChatTelegramTeamTagBg
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.roleAccentColor

/** ARGB colors for incoming chat sender line (shared by chat header and push notifications). */
object ChatSenderLineColors {
    fun serverColorArgb(): Int = SquadRelayPrimary.copy(alpha = 0.9f).toArgb()

    fun tagColorArgb(): Int = ChatTelegramTeamTagBg.copy(alpha = 0.95f).toArgb()

    fun nicknameColorArgb(squadRole: String?): Int =
        roleAccentColor(squadRole?.trim()?.uppercase().orEmpty().ifBlank { "R1" }).toArgb()
}
