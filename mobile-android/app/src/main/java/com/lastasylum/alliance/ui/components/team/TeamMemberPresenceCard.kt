package com.lastasylum.alliance.ui.components.team

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val scheme = MaterialTheme.colorScheme
    val ingameRing = Color(0xFF2E7D32)
    val statusCd = stringResource(
        if (inGameNow) R.string.team_member_in_game_cd else R.string.team_member_not_in_game_cd,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface.copy(alpha = 0.58f),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        if (showIngameAvatarRing) {
                            Modifier
                                .clip(CircleShape)
                                .background(ingameRing.copy(alpha = 0.35f))
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
                        .background(scheme.primaryContainer),
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
                            color = scheme.onPrimaryContainer,
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
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface,
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
                        color = scheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.semantics { contentDescription = roleCd },
                    ) {
                        Text(
                            text = squadRole,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = statusCd,
                        modifier = Modifier.size(8.dp),
                        tint = if (inGameNow) ingameRing else scheme.outline.copy(alpha = 0.55f),
                    )
                    if (presenceSubtitle.isNotBlank()) {
                        Text(
                            text = presenceSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            trailingContent?.invoke()
        }
    }
}
