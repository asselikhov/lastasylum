package com.lastasylum.alliance.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.components.team.FeedCardDesignTokens
import com.lastasylum.alliance.ui.components.team.PremiumJournalFeedTokens
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
import com.lastasylum.alliance.ui.theme.premium.PremiumColors

@Composable
fun FeedCardUnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(FeedCardDesignTokens.unreadDotSize)
            .clip(CircleShape)
            .background(FeedCardDesignTokens.unreadDotColor),
    )
}

@Composable
fun FeedCardUnreadTonalBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = PremiumColors.accentCyan.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            PremiumColors.accentCyan.copy(alpha = 0.42f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = PremiumColors.accentCyanBright,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun FeedCardTypePill(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = PremiumColors.accentPurple.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            PremiumColors.accentPurple.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
fun FeedCardStatChip(
    label: String,
    leadingIcon: ImageVector?,
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.72f),
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White.copy(alpha = 0.55f),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FeedCardMetaRow(
    username: String,
    telegramUsername: String?,
    trailingMeta: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val avatarUrl = com.lastasylum.alliance.ui.util.telegramAvatarUrl(telegramUsername)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (avatarUrl != null) {
            ChatSenderAvatar(
                telegramUrl = avatarUrl,
                size = FeedCardDesignTokens.avatarMeta,
                fallbackName = username,
            )
        } else {
            val initial = username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Box(
                modifier = Modifier
                    .size(FeedCardDesignTokens.avatarMeta)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SquadRelayPrimary.copy(0.85f), SquadRelaySecondary.copy(0.75f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        Text(
            text = username,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailingMeta.isNotBlank()) {
            Text(
                text = trailingMeta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingContent?.invoke()
    }
}

@Composable
fun FeedCardPollHeaderStrip(
    pollLabel: String,
    votesLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FeedCardDesignTokens.pollHeaderHeight)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        PremiumColors.pollGradientStart.copy(alpha = 0.45f),
                        PremiumColors.pollGradientEnd.copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = FeedCardDesignTokens.contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Poll,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(22.dp),
            )
            FeedCardTypePill(label = pollLabel, icon = Icons.Outlined.Poll)
            votesLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
fun FeedCardHero(
    imageRequest: ImageRequest?,
    title: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    topOverlay: @Composable (() -> Unit)? = null,
) {
    val topShape = RoundedCornerShape(
        topStart = FeedCardDesignTokens.compactCornerRadius,
        topEnd = FeedCardDesignTokens.compactCornerRadius,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FeedCardDesignTokens.heroHeightNews)
            .clip(topShape),
    ) {
        imageRequest?.let { req ->
            AsyncImage(
                model = req,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.48f),
                            Color.Black.copy(alpha = 0.84f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = FeedCardDesignTokens.compactCardPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            topOverlay?.invoke()
            Text(
                text = title,
                style = PremiumJournalFeedTokens.titleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FeedCardUnreadCountPill(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val shown = if (count > 99) 99 else count
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = PremiumColors.accentCyan.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            PremiumColors.accentCyan.copy(alpha = 0.35f),
        ),
    ) {
        Text(
            text = shown.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = PremiumColors.accentCyanBright,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
