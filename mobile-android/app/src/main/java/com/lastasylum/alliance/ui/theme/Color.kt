package com.lastasylum.alliance.ui.theme

import androidx.compose.ui.graphics.Color

val SquadRelayPrimary = Color(0xFF8E6CFF)
val SquadRelayOnPrimary = Color(0xFFF9F6FF)
val SquadRelaySecondary = Color(0xFF2DD4BF)
val SquadRelayOnSecondary = Color(0xFF041312)
val SquadRelayTertiary = Color(0xFF94A3B8)
val SquadRelayOnTertiary = Color(0xFF090B10)
val SquadRelayBackground = Color(0xFF090B10)
val SquadRelayOnBackground = Color(0xFFE8EAEF)
val SquadRelaySurface = Color(0xFF121621)
val SquadRelayOnSurface = Color(0xFFE8EAEF)
val SquadRelaySurfaceVariant = Color(0xFF252A38)
val SquadRelayOnSurfaceVariant = Color(0xFFB4BAC8)
val SquadRelaySurfaceHigh = Color(0xFF1A1F2B)
val SquadRelayError = Color(0xFFFF6464)
val SquadRelayOnError = Color(0xFF1A0505)
val SquadRelayOutline = Color(0xFF3D4558)
val SquadRelayOutlineVariant = Color(0xFF2A3142)

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
