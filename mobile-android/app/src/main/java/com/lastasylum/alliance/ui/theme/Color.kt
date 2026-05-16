package com.lastasylum.alliance.ui.theme

import androidx.compose.ui.graphics.Color

val SquadRelayPrimary = Color(0xFF8B5CF6)
val SquadRelayOnPrimary = Color(0xFFFFFFFF)
val SquadRelaySecondary = Color(0xFF38BDF8)
val SquadRelayOnSecondary = Color(0xFF041018)
val SquadRelaySecondaryContainer = Color(0xFF0C1924)
val SquadRelayOnSecondaryContainer = Color(0xFFE0F2FE)
val SquadRelayTertiary = Color(0xFF94A3B8)
val SquadRelayOnTertiary = Color(0xFF0B1020)
val SquadRelayTertiaryContainer = Color(0xFF1A2234)
val SquadRelayOnTertiaryContainer = Color(0xFFE2E8F0)
val SquadRelayBackground = Color(0xFF060816)
val SquadRelayOnBackground = Color(0xFFF3F4F6)
/** M3 surface — близко к void, чтобы полупрозрачные панели не «светились». */
val SquadRelaySurface = Color(0xFF0C101C)
val SquadRelayOnSurface = Color(0xFFF3F4F6)
val SquadRelaySurfaceVariant = Color(0xFF141B2E)
val SquadRelayOnSurfaceVariant = Color(0xFF94A3B8)
val SquadRelaySurfaceLowest = Color(0xFF04060D)
val SquadRelaySurfaceLow = Color(0xFF080C16)
val SquadRelaySurfaceHigh = Color(0xFF121A2C)
val SquadRelaySurfaceHighest = Color(0xFF1A2236)
val SquadRelayError = Color(0xFFEF4444)
val SquadRelayOnError = Color(0xFFFFFFFF)
val SquadRelayErrorContainer = Color(0xFF3F1818)
val SquadRelayOnErrorContainer = Color(0xFFFFE4E6)
val SquadRelayOutline = Color(0xFF334155)
val SquadRelayOutlineVariant = Color(0xFF1E293B)
val SquadRelayScrim = Color(0xE6000000)

/** Vertical void ramp for [AtmosphericBackground] (not wired into M3 tokens). */
val SquadRelayVoidTop = Color(0xFF070B14)
val SquadRelayVoidBottom = Color(0xFF04060D)

val SquadRelayAtmosphericPurple = Color(0xFF8B5CF6)
val SquadRelayAtmosphericSky = Color(0xFF38BDF8)

/** Own-message bubble (primary-adjacent, readable on dark). */
val SquadRelayMineBubble = Color(0xFF2A1F45)
val SquadRelayMineOnBubble = Color(0xFFF5F3FF)

/** Telegram-like chat bubbles (used only in chat screen). */
val ChatTelegramOutgoingBubble = Color(0xFF5288C4)
val ChatTelegramOutgoingOnBubble = Color(0xFFFFFFFF)
val ChatTelegramIncomingBubble = Color(0xFF1E2C3A)
val ChatTelegramIncomingOnBubble = Color(0xFFE8EEF5)
val ChatTelegramTeamTagBg = Color(0xFF2D8A5C)
val ChatTelegramTeamTagFg = Color(0xFFFFFFFF)
val ChatTelegramTimeMuted = Color(0x99FFFFFF)
val ChatTelegramTimeMutedIncoming = Color(0x99A8B5C8)

fun roleAccentColor(role: String): Color = when (role) {
    "R5" -> Color(0xFFFFD54F)
    "R4" -> Color(0xFFD4A5FF)
    "R3" -> Color(0xFF82B1FF)
    "R2" -> Color(0xFFB0BEC5)
    else -> Color(0xFF90A4AE)
}

fun roleOnAccentColor(role: String): Color = when (role) {
    "R5" -> Color(0xFF1A1500)
    "R4", "R3" -> Color(0xFF0D1118)
    else -> Color(0xFF0D1118)
}
