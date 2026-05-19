package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R

data class OverlayGameStatusHudState(
    val allianceChatUnread: Int = 0,
    val teamNewsUnread: Int = 0,
    val forumUnread: Int = 0,
)

@Composable
fun OverlayGameStatusHud(
    state: OverlayGameStatusHudState,
    onMailClick: () -> Unit,
    onNewsClick: () -> Unit,
    onForumClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = Color(0xCC10141E),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayGameStatusHudChip(
            kind = HudChipKind.Forum,
            count = state.forumUnread,
            onClick = onForumClick,
        )
        OverlayGameStatusHudChip(
            kind = HudChipKind.Mail,
            count = state.allianceChatUnread,
            onClick = onMailClick,
        )
        OverlayGameStatusHudChip(
            kind = HudChipKind.News,
            count = state.teamNewsUnread,
            onClick = onNewsClick,
        )
    }
}

private enum class HudChipKind {
    Forum,
    Mail,
    News,
}

@Composable
private fun OverlayGameStatusHudChip(
    kind: HudChipKind,
    count: Int,
    onClick: () -> Unit,
) {
    val label = when (kind) {
        HudChipKind.Forum -> stringResource(R.string.overlay_hud_forum_cd, count)
        HudChipKind.Mail -> stringResource(R.string.overlay_hud_alliance_chat_cd, count)
        HudChipKind.News -> stringResource(R.string.overlay_hud_news_cd, count)
    }
    val icon: ImageVector = when (kind) {
        HudChipKind.Forum -> Icons.Outlined.Forum
        HudChipKind.Mail -> Icons.Outlined.Email
        HudChipKind.News -> Icons.AutoMirrored.Outlined.Article
    }
    val tint = when (kind) {
        HudChipKind.Forum -> Color(0xFFCE93D8)
        HudChipKind.Mail -> Color(0xFF4DB6AC)
        HudChipKind.News -> Color(0xFF90CAF9)
    }
    val badge = count.coerceAtLeast(0)
    Box(
        modifier = Modifier
            .background(
                color = Color(0x661A1F2B),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        if (badge > 0) {
            val badgeText = if (badge > 99) "99+" else badge.toString()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-6).dp)
                    .background(Color(0xFFE53935), CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 9.sp,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}
