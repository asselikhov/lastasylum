package com.lastasylum.alliance.ui.theme.premium

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val platform = PlatformTextStyle(includeFontPadding = false)
private val display = FontFamily.SansSerif

/** Premium typography — larger titles, muted meta. Wired via [com.lastasylum.alliance.ui.theme.SquadRelayTypography]. */
val PremiumTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
        platformStyle = platform,
    ),
    headlineMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        platformStyle = platform,
    ),
    headlineSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        platformStyle = platform,
    ),
    titleLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        platformStyle = platform,
    ),
    titleMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        platformStyle = platform,
    ),
    titleSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        platformStyle = platform,
    ),
    bodyLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        platformStyle = platform,
    ),
    bodyMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        platformStyle = platform,
    ),
    bodySmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        platformStyle = platform,
    ),
    labelLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
        platformStyle = platform,
    ),
    labelMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
        platformStyle = platform,
    ),
    labelSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.5.sp,
        platformStyle = platform,
    ),
)
