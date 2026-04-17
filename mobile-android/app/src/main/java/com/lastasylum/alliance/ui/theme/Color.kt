package com.lastasylum.alliance.ui.theme

import androidx.compose.ui.graphics.Color

val SquadRelayPrimary = Color(0xFF9B7CFF)
val SquadRelayOnPrimary = Color(0xFF1A0D33)
val SquadRelaySecondary = Color(0xFF2DD4BF)
val SquadRelayOnSecondary = Color(0xFF001A16)
val SquadRelaySecondaryContainer = Color(0xFF0D2A26)
val SquadRelayOnSecondaryContainer = Color(0xFFB5FFF0)
val SquadRelayTertiary = Color(0xFF94A3B8)
val SquadRelayOnTertiary = Color(0xFF0B1018)
val SquadRelayTertiaryContainer = Color(0xFF1E2836)
val SquadRelayOnTertiaryContainer = Color(0xFFD7DEEA)
val SquadRelayBackground = Color(0xFF070910)
val SquadRelayOnBackground = Color(0xFFE8EAEF)
val SquadRelaySurface = Color(0xFF10141E)
val SquadRelayOnSurface = Color(0xFFE8EAEF)
val SquadRelaySurfaceVariant = Color(0xFF252A38)
val SquadRelayOnSurfaceVariant = Color(0xFFB4BAC8)
val SquadRelaySurfaceLowest = Color(0xFF05070C)
val SquadRelaySurfaceLow = Color(0xFF0C1018)
val SquadRelaySurfaceHigh = Color(0xFF1A1F2B)
val SquadRelaySurfaceHighest = Color(0xFF222836)
val SquadRelayError = Color(0xFFFF6B6B)
val SquadRelayOnError = Color(0xFF2A0505)
val SquadRelayErrorContainer = Color(0xFF3D1518)
val SquadRelayOnErrorContainer = Color(0xFFFFD1D4)
val SquadRelayOutline = Color(0xFF4A5368)
val SquadRelayOutlineVariant = Color(0xFF2E3545)
val SquadRelayScrim = Color(0xE6000000)

/** Own-message bubble (primary-adjacent, readable on dark). */
val SquadRelayMineBubble = Color(0xFF35285A)
val SquadRelayMineOnBubble = Color(0xFFE8DFFF)

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
