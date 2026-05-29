package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.ui.theme.premium.PremiumColors

enum class JournalFeedVariant {
    News,
    Poll,
    PollOnly,
    UnreadNews,
}

object PremiumJournalFeedTokens {
    val cardShape = RoundedCornerShape(22.dp)
    val cardInnerShape = RoundedCornerShape(20.dp)
    val optionTileShape = RoundedCornerShape(12.dp)
    val chipShape = RoundedCornerShape(10.dp)
    val timePillShape = RoundedCornerShape(8.dp)
    val borderWidth = 1.25.dp
    val accentRailWidth = 4.dp
    val cardPaddingH = 18.dp
    val cardPaddingV = 16.dp
    val pollCardPaddingV = 14.dp
    val listSpacing = 14.dp
    val sectionGap = 12.dp
    val pollSectionGap = 8.dp
    val optionTilePaddingH = 12.dp
    val optionTilePaddingV = 8.dp
    val optionTileMinHeight = 40.dp
    val optionTileMinHeightFull = 48.dp
    val pollPreviewBarHeight = 6.dp
    val pollPreviewOptionSpacing = 6.dp

    val pressScale = 0.982f
    val pressAnimSpec = tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)
    val listEnterAnimMs = 280

    val InterFamily = ForumTopicCardTokens.InterFamily
    private val platformStyle = PlatformTextStyle(includeFontPadding = false)

    val titleColor = Color(0xFFF8FAFF)
    val excerptColor = Color(0xFF94A8C0)
    val metaColor = Color(0xFF8FA4BC)
    val metaOnHero = Color(0xFFE8F0FA)

    val glassTop = Color(0xFF121A28)
    val glassBottom = Color(0xFF0A101C)
    val glassAlphaDefault = 0.90f
    val glassAlphaUnread = 0.94f

    val optionTileTop = Color(0xFF1A2434)
    val optionTileBottom = Color(0xFF121A28)
    val optionTrackColor = Color(0xFF1E2836)

    val titleStyle: TextStyle = ForumTopicCardTokens.titleStyle

    val headlineStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.12.sp,
        color = titleColor,
        platformStyle = platformStyle,
    )

    val excerptStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = excerptColor,
        platformStyle = platformStyle,
    )

    val metaStyle: TextStyle = ForumTopicCardTokens.metaStyle.copy(color = metaColor)

    val optionLabelStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        color = titleColor,
        platformStyle = platformStyle,
    )

    val optionPctStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        color = PremiumColors.accentCyanBright,
        platformStyle = platformStyle,
    )

    fun sectionSpacing(variant: JournalFeedVariant): Dp = when (variant) {
        JournalFeedVariant.Poll,
        JournalFeedVariant.PollOnly,
        -> pollSectionGap
        else -> sectionGap
    }

    fun contentPaddingTop(hasHero: Boolean, variant: JournalFeedVariant): Dp = when {
        hasHero -> 12.dp
        variant == JournalFeedVariant.Poll || variant == JournalFeedVariant.PollOnly -> pollCardPaddingV
        else -> cardPaddingV
    }

    @Immutable
    data class Accent(val primary: Color, val secondary: Color)

    fun accentFor(variant: JournalFeedVariant): Accent = when (variant) {
        JournalFeedVariant.News,
        JournalFeedVariant.UnreadNews,
        -> Accent(PremiumColors.accentCyan, Color(0xFF6366F1))
        JournalFeedVariant.Poll,
        JournalFeedVariant.PollOnly,
        -> Accent(Color(0xFF818CF8), PremiumColors.accentPurpleDeep)
    }

    fun glassFill(alpha: Float = glassAlphaDefault): Brush =
        Brush.verticalGradient(
            colors = listOf(
                glassTop.copy(alpha = alpha),
                glassBottom.copy(alpha = alpha * 0.98f),
            ),
        )

    fun gradientBorderColors(
        accent: Accent,
        glowBoost: Float,
        unread: Boolean,
    ): List<Color> {
        val boost = glowBoost.coerceIn(1f, 1.4f)
        val edgeA = if (unread) 0.58f else 0.36f
        return listOf(
            accent.primary.copy(alpha = edgeA * boost),
            PremiumColors.accentCyan.copy(alpha = edgeA * 0.75f * boost),
            accent.secondary.copy(alpha = edgeA * 0.62f * boost),
            Color(0xFF0D1524).copy(alpha = edgeA * 0.35f * boost),
        )
    }

    fun railColor(variant: JournalFeedVariant, unread: Boolean): Color = when {
        unread -> PremiumColors.accentCyanBright
        variant == JournalFeedVariant.Poll || variant == JournalFeedVariant.PollOnly ->
            Color(0xFF818CF8)
        else -> PremiumColors.accentCyan
    }
}
