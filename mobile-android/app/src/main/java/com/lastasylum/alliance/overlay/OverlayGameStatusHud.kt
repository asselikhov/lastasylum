package com.lastasylum.alliance.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
    OverlayGameHudBar(modifier = modifier) {
        OverlayGameHudChipRow {
            OverlayGameHudChip(
                icon = Icons.Outlined.Forum,
                tint = Color(0xFFCE93D8),
                badgeCount = state.forumUnread,
                contentDescription = stringResource(R.string.overlay_hud_forum_cd, state.forumUnread),
                onClick = onForumClick,
            )
            OverlayGameHudChip(
                icon = Icons.Outlined.Email,
                tint = Color(0xFF4DB6AC),
                badgeCount = state.allianceChatUnread,
                contentDescription = stringResource(
                    R.string.overlay_hud_alliance_chat_cd,
                    state.allianceChatUnread,
                ),
                onClick = onMailClick,
            )
            OverlayGameHudChip(
                icon = Icons.AutoMirrored.Outlined.Article,
                tint = Color(0xFF90CAF9),
                badgeCount = state.teamNewsUnread,
                contentDescription = stringResource(R.string.overlay_hud_news_cd, state.teamNewsUnread),
                onClick = onNewsClick,
            )
        }
    }
}
