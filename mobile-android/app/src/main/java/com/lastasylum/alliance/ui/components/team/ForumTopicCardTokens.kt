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
    val cardShape = RoundedCornerShape(FeedCardDesignTokens.compactCornerRadius)
    val cardInnerShape = RoundedCornerShape(FeedCardDesignTokens.compactInnerCornerRadius)
    val borderWidth = FeedCardDesignTokens.compactBorderWidth
    val cardPaddingH = FeedCardDesignTokens.compactCardPadding
    val cardPaddingV = FeedCardDesignTokens.compactCardPadding
    val listSpacing = FeedCardDesignTokens.compactListSpacing
    val rowGap = FeedCardDesignTokens.compactRowGap
    val avatarOuter = FeedCardDesignTokens.compactAvatar
    val avatarInner = 34.dp
    val avatarRingWidth = 1.5.dp
    val titleMetaGap = FeedCardDesignTokens.compactTitleMetaGap
    val chipGap = 6.dp
    val chipRadius = 10.dp
    val chipPaddingH = 8.dp
    val chipPaddingV = 3.dp
    val ghostButtonSize = 32.dp
    val ghostIconSize = 16.dp
    val activityStripHeight = 2.dp
    val activityStripHeightHot = 2.5.dp
    val activityDotSize = 6.dp
    val accentRailWidth = FeedCardDesignTokens.compactAccentRailWidth

    /** Shared fire colors for Hot card border + flame layers. */
    object FirePalette {
        val amber = Color(0xFFE8A030)
        val orange = Color(0xFFFF6B35)
        val deep = Color(0xFFC45C20)
        val coal = Color(0xFF8B2500)
        val smoke = Color(0xFF2A1810)
        val core = Color(0xFFFFF3D4)
    }

    val pressScale = 0.985f
    val pressAnimSpec = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)

    val InterFamily = FontFamily(
        Font(R.font.inter, FontWeight.Normal),
        Font(R.font.inter, FontWeight.Medium),
        Font(R.font.inter, FontWeight.SemiBold),
        Font(R.font.inter, FontWeight.Bold),
    )

    private val platformStyle = PlatformTextStyle(includeFontPadding = false)

    val titleStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = FeedCardDesignTokens.compactTitleSize,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
        color = Color(0xFFF8FAFF),
        platformStyle = platformStyle,
    )

    val metaStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = FeedCardDesignTokens.compactMetaSize,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
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
        ActivityLevel.Hot -> 0.92f
        ActivityLevel.Warm -> 0.86f
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
                FirePalette.amber.copy(alpha = warmA),
                FirePalette.orange.copy(alpha = warmA * 0.92f),
                FirePalette.deep.copy(alpha = warmA * 0.82f),
                accent.primary.copy(alpha = warmA * 0.45f),
            )
        }
        val cyanA = when (level) {
            ActivityLevel.Warm -> 0.48f
            ActivityLevel.Calm -> 0.30f
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
