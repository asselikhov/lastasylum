package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericSky
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
import com.lastasylum.alliance.ui.theme.premium.PremiumColors

object ForumTopicCardTokens {
    val cardShape = RoundedCornerShape(22.dp)
    val cardInnerShape = RoundedCornerShape(20.dp)
    val borderWidth = 1.25.dp
    val cardPaddingH = 18.dp
    val cardPaddingV = 18.dp
    val listSpacing = 14.dp
    val rowGap = 14.dp
    val avatarOuter = 54.dp
    val avatarInner = 46.dp
    val avatarRingWidth = 2.dp
    val titleMetaGap = 10.dp
    val chipGap = 8.dp
    val chipRadius = 12.dp
    val chipPaddingH = 10.dp
    val chipPaddingV = 5.dp
    val ghostButtonSize = 36.dp
    val ghostIconSize = 18.dp
    val activityStripHeight = 2.dp
    val activityDotSize = 7.dp

    val pressScale = 0.982f
    val pressAnimSpec = tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)

    val InterFamily = FontFamily(
        Font(R.font.inter, FontWeight.Normal),
        Font(R.font.inter, FontWeight.Medium),
        Font(R.font.inter, FontWeight.SemiBold),
        Font(R.font.inter, FontWeight.Bold),
    )

    private val platformStyle = PlatformTextStyle(includeFontPadding = false)

    val titleStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        color = Color(0xFFF8FAFF),
        platformStyle = platformStyle,
    )

    val metaStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.25.sp,
        color = Color(0xFF8FA4BC),
        platformStyle = platformStyle,
    )

    val chipFill = Color(0xFF162032).copy(alpha = 0.72f)
    val chipBorder = Color(0xFF4A7CFF).copy(alpha = 0.22f)
    val chipBorderHot = PremiumColors.accentCyan.copy(alpha = 0.38f)
    val metaText = Color(0xFF94A8C0)
    val metaIcon = Color(0xFF6E86A0)

    enum class ActivityLevel { Calm, Warm, Hot }

    @Immutable
    data class Accent(val primary: Color, val secondary: Color)

    fun accentForIndex(index: Int): Accent {
        val palette = listOf(
            Accent(PremiumColors.accentCyan, Color(0xFF6366F1)),
            Accent(SquadRelayPrimary, SquadRelayAtmosphericSky),
            Accent(SquadRelaySecondary, Color(0xFF818CF8)),
            Accent(Color(0xFF22D3EE), PremiumColors.accentPurpleDeep),
        )
        return palette[index % palette.size]
    }

    fun activityLevel(unreadCount: Int, messageCount: Int): ActivityLevel = when {
        unreadCount > 0 -> ActivityLevel.Hot
        messageCount >= 3 -> ActivityLevel.Warm
        else -> ActivityLevel.Calm
    }

    fun glassAlpha(level: ActivityLevel): Float = when (level) {
        ActivityLevel.Hot -> 0.94f
        ActivityLevel.Warm -> 0.88f
        ActivityLevel.Calm -> 0.82f
    }

    fun glassFill(alpha: Float): Color = Color(0xFF0A101C).copy(alpha = alpha)

    fun gradientBorderColors(
        accent: Accent,
        level: ActivityLevel,
        glowBoost: Float,
    ): List<Color> {
        val boost = glowBoost.coerceIn(1f, 1.4f)
        if (level == ActivityLevel.Hot) {
            val warmA = 0.78f * boost
            return listOf(
                Color(0xFFE8A030).copy(alpha = warmA),
                Color(0xFFFF6B35).copy(alpha = warmA * 0.92f),
                Color(0xFFC45C20).copy(alpha = warmA * 0.82f),
                accent.primary.copy(alpha = warmA * 0.45f),
            )
        }
        val cyanA = when (level) {
            ActivityLevel.Warm -> 0.52f
            ActivityLevel.Calm -> 0.34f
            ActivityLevel.Hot -> 0.72f
        } * boost
        return listOf(
            accent.primary.copy(alpha = cyanA),
            PremiumColors.accentCyan.copy(alpha = cyanA * 0.85f),
            accent.secondary.copy(alpha = cyanA * 0.75f),
            PremiumColors.accentPurpleDeep.copy(alpha = cyanA * 0.55f),
        )
    }

    @Composable
    @ReadOnlyComposable
    fun chipTextColor(hot: Boolean = false): Color =
        if (hot) PremiumColors.accentCyanBright.copy(alpha = 0.92f) else metaText

    /** Dark gold — readable on light and dark surfaces (overlay news / polls). */
    val feedTitleColor = Color(0xFFC4A035)
    val feedTitleColorDark = Color(0xFFD4AF37)

    val feedTitleStyle = titleStyle.copy(
        color = feedTitleColor,
        fontWeight = FontWeight.Bold,
    )
}
