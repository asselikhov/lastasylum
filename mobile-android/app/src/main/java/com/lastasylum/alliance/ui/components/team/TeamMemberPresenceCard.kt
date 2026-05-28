package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.components.OverlayMemberVoiceBadges
import com.lastasylum.alliance.ui.components.premium.PremiumFeedCardShell
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.util.telegramAvatarUrl

@Composable
fun TeamMemberPresenceCard(
    username: String,
    telegramUsername: String?,
    squadRole: String,
    displayName: String,
    presenceSubtitle: String,
    inGameNow: Boolean,
    showIngameAvatarRing: Boolean,
    micOn: Boolean,
    soundOn: Boolean,
    showVoiceBadges: Boolean,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val avatarUrl = telegramAvatarUrl(telegramUsername)
    val letter = username.trim().take(1).uppercase().ifBlank { "?" }
    val roleCd = stringResource(R.string.overlay_member_squad_rank_cd, squadRole)
    val roleColor = roleAccentColor(squadRole)
    val statusCd = stringResource(
        if (inGameNow) R.string.team_member_in_game_cd else R.string.team_member_not_in_game_cd,
    )
    val ringPulse by animateFloatAsState(
        targetValue = if (inGameNow && showIngameAvatarRing) 1f else 0.55f,
        animationSpec = tween(900),
        label = "ingameRingPulse",
    )

    PremiumFeedCardShell(
        onClick = null,
        modifier = modifier.fillMaxWidth(),
        variant = FeedCardVariant.Member,
        showLiveAccent = inGameNow,
        accentColor = if (inGameNow) PremiumColors.liveIndicator else null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp,
            vertical = 12.dp,
        ),
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(FeedCardDesignTokens.avatarMember)
                        .then(
                            if (showIngameAvatarRing) {
                                Modifier
                                    .clip(CircleShape)
                                    .background(
                                        PremiumColors.liveIndicator.copy(alpha = 0.22f + 0.18f * ringPulse),
                                    )
                                    .padding(2.dp)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .then(
                                if (showIngameAvatarRing) {
                                    Modifier.border(2.dp, PremiumColors.liveIndicator, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        SquadRelayPrimary.copy(0.85f),
                                        SquadRelaySecondary.copy(0.75f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (inGameNow) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (showVoiceBadges) {
                            OverlayMemberVoiceBadges(
                                micOn = micOn,
                                soundOn = soundOn,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = roleColor.copy(alpha = 0.18f),
                            modifier = Modifier.semantics { contentDescription = roleCd },
                        ) {
                            Text(
                                text = squadRole,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = roleColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = statusCd,
                            modifier = Modifier.size(8.dp),
                            tint = if (inGameNow) {
                                PremiumColors.liveIndicator
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                            },
                        )
                        if (presenceSubtitle.isNotBlank()) {
                            Text(
                                text = presenceSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                trailingContent?.invoke()
            }
        },
    )
}
