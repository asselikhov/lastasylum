package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

data class OverlayHudAvatarPreview(
    val userId: String,
    val avatarRelativeUrl: String?,
    val username: String,
)

@Composable
fun OverlayHudFacepile(
    previews: List<OverlayHudAvatarPreview>,
    totalCount: Int,
    avatarSize: Dp = 18.dp,
    overlap: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    if (previews.isEmpty()) return
    val shown = previews.take(3)
    val extra = (totalCount - shown.size).coerceAtLeast(0)
    Box(modifier = modifier) {
        shown.forEachIndexed { index, preview ->
            FacepileAvatar(
                preview = preview,
                size = avatarSize,
                modifier = Modifier.offset(x = (avatarSize - overlap) * index),
            )
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (avatarSize - overlap) * shown.size)
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(Color(0xFF2A3344))
                    .border(1.dp, Color(0x559B7CFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$extra",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun FacepileAvatar(
    preview: OverlayHudAvatarPreview,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val avatarModel = preview.avatarRelativeUrl?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { com.lastasylum.alliance.ui.util.profileAvatarImageRequestOrChatFallback(ctx, it) }
    val letter = preview.username.trim().take(1).uppercase().ifBlank { "?" }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1E2A3A))
            .border(1.dp, Color(0xFF10141E), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarModel != null) {
            AsyncImage(
                model = avatarModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB8C4D9),
            )
        }
    }
}

fun buildOverlayHudAvatarPreviews(
    teamId: String,
    selfUserId: String?,
    limit: Int = 3,
): List<OverlayHudAvatarPreview> {
    val tid = teamId.trim()
    if (tid.isEmpty()) return emptyList()
    val presence = OverlayTeamPresenceCache.peek(tid) ?: return emptyList()
    return filterFreshIngameRecipients(presence.ingame, selfUserId)
        .take(limit)
        .map { member ->
            OverlayHudAvatarPreview(
                userId = member.userId,
                avatarRelativeUrl = member.avatarRelativeUrl,
                username = member.username,
            )
        }
}
