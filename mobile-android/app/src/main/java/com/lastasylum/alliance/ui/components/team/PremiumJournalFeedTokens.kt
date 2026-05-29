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
    val optionTileShape = RoundedCornerShape(14.dp)
    val chipShape = RoundedCornerShape(10.dp)
    val timePillShape = RoundedCornerShape(8.dp)
    val borderWidth = 1.25.dp
    val accentRailWidth = 4.dp
    val cardPaddingH = 18.dp
    val cardPaddingV = 16.dp
    val listSpacing = 14.dp
    val sectionGap = 12.dp
    val optionTilePaddingH = 14.dp
    val optionTilePaddingV = 12.dp

    val pressScale = 0.982f
    val pressAnimSpec = tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)
    val listEnterAnimMs = 280

    val InterFamily = ForumTopicCardTokens.InterFamily
    private val platformStyle = PlatformTextStyle(includeFontPadding = false)

    val titleColor = Color(0xFFF4F7FF)
    val excerptColor = Color(0xFF94A8C0)
    val metaColor = Color(0xFF7A8FA6)
    val metaOnHero = Color(0xFFE8F0FA)

    val glassTop = Color(0xFF4A5668)
    val glassBottom = Color(0xFF2E3848)
    val glassAlphaDefault = 0.88f
    val glassAlphaUnread = 0.92f

    val titleStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.1.sp,
        color = titleColor,
        platformStyle = platformStyle,
    )

    val headlineStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
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

    val metaStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp,
        color = metaColor,
        platformStyle = platformStyle,
    )

    val optionLabelStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = titleColor,
        platformStyle = platformStyle,
    )

    val optionPctStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        color = PremiumColors.accentCyanBright,
        platformStyle = platformStyle,
    )

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
                glassBottom.copy(alpha = alpha * 0.96f),
            ),
        )

    fun gradientBorderColors(
        accent: Accent,
        glowBoost: Float,
        unread: Boolean,
    ): List<Color> {
        val boost = glowBoost.coerceIn(1f, 1.4f)
        val edgeA = if (unread) 0.68f else 0.42f
        return listOf(
            Color.White.copy(alpha = edgeA * boost),
            accent.primary.copy(alpha = edgeA * 0.85f * boost),
            accent.secondary.copy(alpha = edgeA * 0.72f * boost),
            PremiumColors.accentPurpleDeep.copy(alpha = edgeA * 0.45f * boost),
        )
    }

    fun railColor(variant: JournalFeedVariant, unread: Boolean): Color = when {
        unread -> PremiumColors.accentCyanBright
        variant == JournalFeedVariant.Poll || variant == JournalFeedVariant.PollOnly ->
            Color(0xFF818CF8)
        else -> PremiumColors.accentCyan
    }
}
