package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericSky
import com.lastasylum.alliance.ui.theme.SquadRelayError
import com.lastasylum.alliance.ui.theme.SquadRelayOnSurface
import com.lastasylum.alliance.ui.theme.SquadRelayOnSurfaceVariant
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
object ForumTopicCardTokens {
    val cardRadius = 16.dp
    val cardPaddingH = 14.dp
    val cardPaddingV = 12.dp
    val avatarSize = 36.dp
    val rowGap = 12.dp
    val titleMetaGap = 6.dp
    val chipGap = 6.dp
    val chipRadius = 10.dp
    val chipPaddingH = 10.dp
    val chipPaddingV = 4.dp
    val ghostButtonSize = 32.dp
    val ghostIconSize = 20.dp
    val unreadDotSize = 6.dp

    val pressScale = 0.985f
    val pressAnimSpec = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)
    val pressElevationAnimSpec = tween<Dp>(durationMillis = 120, easing = FastOutSlowInEasing)
    val elevationRest = 0.dp
    val elevationPressed = 3.dp

    val InterFamily = FontFamily(
        Font(R.font.inter, FontWeight.Normal),
        Font(R.font.inter, FontWeight.Medium),
        Font(R.font.inter, FontWeight.SemiBold),
    )

    private val platformStyle = PlatformTextStyle(includeFontPadding = false)

    val titleStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        color = Color(0xFFF9FAFB),
        platformStyle = platformStyle,
    )

    val chipStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
        color = Color.White.copy(alpha = 0.72f),
        platformStyle = platformStyle,
    )

    val cardBaseTop = Color(0xFF0A0F1A)
    val cardBaseBottom = Color(0xFF0E1424)
    val chipFill = Color.White.copy(alpha = 0.07f)
    val chipBorder = Color.White.copy(alpha = 0.10f)
    val topHighlight = Color.White.copy(alpha = 0.05f)
    val ghostBgRest = Color.Transparent
    val ghostBgPressed = Color.White.copy(alpha = 0.08f)

    @Immutable
    data class Accent(val primary: Color, val secondary: Color)

    fun accentForIndex(index: Int): Accent {
        val palette = listOf(
            Accent(SquadRelayPrimary, SquadRelayAtmosphericSky),
            Accent(SquadRelaySecondary, Color(0xFF818CF8)),
            Accent(Color(0xFF34D399), SquadRelaySecondary),
            Accent(Color(0xFFF472B6), SquadRelayPrimary),
        )
        return palette[index % palette.size]
    }

    @Composable
    @ReadOnlyComposable
    fun chipTextColor(): Color = SquadRelayOnSurfaceVariant.copy(alpha = 0.88f)

    @Composable
    @ReadOnlyComposable
    fun titleColor(): Color = SquadRelayOnSurface

    val unreadDotColor: Color = SquadRelayError
}
