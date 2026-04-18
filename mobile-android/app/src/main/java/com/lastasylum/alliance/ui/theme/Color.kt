package com.lastasylum.alliance.ui.theme

import androidx.compose.ui.graphics.Color

val SquadRelayPrimary = Color(0xFFA78BFA)
val SquadRelayOnPrimary = Color(0xFF151126)
val SquadRelaySecondary = Color(0xFF7DD3C7)
val SquadRelayOnSecondary = Color(0xFF081816)
val SquadRelaySecondaryContainer = Color(0xFF122523)
val SquadRelayOnSecondaryContainer = Color(0xFFD6FFF8)
val SquadRelayTertiary = Color(0xFF94A3B8)
val SquadRelayOnTertiary = Color(0xFF0F141B)
val SquadRelayTertiaryContainer = Color(0xFF1C2430)
val SquadRelayOnTertiaryContainer = Color(0xFFD8E1EC)
val SquadRelayBackground = Color(0xFF090B11)
val SquadRelayOnBackground = Color(0xFFF1F3F7)
val SquadRelaySurface = Color(0xFF11151C)
val SquadRelayOnSurface = Color(0xFFF1F3F7)
val SquadRelaySurfaceVariant = Color(0xFF262C36)
val SquadRelayOnSurfaceVariant = Color(0xFFB7BFCC)
val SquadRelaySurfaceLowest = Color(0xFF06080D)
val SquadRelaySurfaceLow = Color(0xFF0D1016)
val SquadRelaySurfaceHigh = Color(0xFF171C25)
val SquadRelaySurfaceHighest = Color(0xFF202631)
val SquadRelayError = Color(0xFFFF7D8C)
val SquadRelayOnError = Color(0xFF29070D)
val SquadRelayErrorContainer = Color(0xFF3B161D)
val SquadRelayOnErrorContainer = Color(0xFFFFD7DC)
val SquadRelayOutline = Color(0xFF475164)
val SquadRelayOutlineVariant = Color(0xFF2C3340)
val SquadRelayScrim = Color(0xE6000000)

/** Own-message bubble (primary-adjacent, readable on dark). */
val SquadRelayMineBubble = Color(0xFF2C2740)
val SquadRelayMineOnBubble = Color(0xFFF0EAFF)

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
