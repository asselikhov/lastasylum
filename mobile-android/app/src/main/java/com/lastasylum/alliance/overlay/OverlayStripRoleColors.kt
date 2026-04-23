package com.lastasylum.alliance.overlay

import android.graphics.Color

/**
 * ARGB для бейджа роли в View-оверлее.
 * Синхронизировать с [com.lastasylum.alliance.ui.theme.roleAccentColor] / [roleOnAccentColor].
 */
fun roleAccentArgb(role: String): Int = when (role.trim()) {
    "R5" -> Color.parseColor("#FFFFD54F")
    "R4" -> Color.parseColor("#FFD4A5FF")
    "R3" -> Color.parseColor("#FF82B1FF")
    "R2" -> Color.parseColor("#FFB0BEC5")
    else -> Color.parseColor("#FF90A4AE")
}

fun roleOnAccentArgb(role: String): Int = when (role.trim()) {
    "R5" -> Color.parseColor("#FF1A1500")
    "R4", "R3" -> Color.parseColor("#FF0D1118")
    else -> Color.parseColor("#FF0D1118")
}
