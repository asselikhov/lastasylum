package com.lastasylum.alliance.ui.components.team

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces

/** Shared dimensions and accents for team feed cards (news, poll, forum, members). */
object FeedCardDesignTokens {
    val heroHeightNews = 180.dp
    val pollHeaderHeight = 56.dp
    val avatarList = 40.dp
    val avatarMeta = 28.dp
    val avatarMember = 48.dp
    val contentPadding = 16.dp
    val sectionGap = 12.dp
    val accentBarWidth = 3.dp
    val unreadDotSize = 6.dp
    val minPollBarProgress = 0.02f

    val unreadBorderColor: Color = PremiumColors.accentCyan
    val unreadDotColor: Color = PremiumColors.accentCyanBright
    val liveRingColor: Color = PremiumColors.liveIndicator

    val layerAlphaDefault = PremiumSurfaces.listCardAlpha
    val layerAlphaUnread = 0.92f
    val layerAlphaIngame = 0.91f
    val layerAlphaOffline = 0.86f

    val shadowRest = 6.dp
    val shadowUnread = 10.dp
    val shadowPressed = 4.dp
    val listShadowElevation = 4.dp
    val detailShadowElevation = 8.dp
    val footerDividerAlpha = 0.06f
}

enum class FeedCardVariant {
    News,
    PollOnly,
    ForumTopic,
    Member,
}
