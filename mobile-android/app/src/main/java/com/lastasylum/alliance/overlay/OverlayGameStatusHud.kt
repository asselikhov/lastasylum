package com.lastasylum.alliance.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R

data class OverlayGameStatusHudState(
    val allianceChatUnread: Int = 0,
    val teamNewsUnread: Int = 0,
    val forumUnread: Int = 0,
    /** Non-null when backend reports a newer APK; shows update chip after alliance chat. */
    val appUpdateDownloadUrl: String? = null,
)

@Composable
fun OverlayGameStatusHud(
    state: OverlayGameStatusHudState,
    onMailClick: () -> Unit,
    onNewsClick: () -> Unit,
    onForumClick: () -> Unit,
    onAppUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayGameHudBar(modifier = modifier) {
        OverlayGameHudChipRow {
            OverlayGameHudChip(
                icon = Icons.AutoMirrored.Outlined.Article,
                accent = OverlayHudChipAccent.News,
                badgeCount = state.teamNewsUnread,
                contentDescription = stringResource(R.string.overlay_hud_news_cd, state.teamNewsUnread),
                onClick = onNewsClick,
            )
            OverlayGameHudChip(
                icon = Icons.Outlined.Forum,
                accent = OverlayHudChipAccent.Forum,
                badgeCount = state.forumUnread,
                contentDescription = stringResource(R.string.overlay_hud_forum_cd, state.forumUnread),
                onClick = onForumClick,
            )
            OverlayGameHudChip(
                icon = Icons.Outlined.Email,
                accent = OverlayHudChipAccent.Mail,
                badgeCount = state.allianceChatUnread,
                contentDescription = stringResource(
                    R.string.overlay_hud_alliance_chat_cd,
                    state.allianceChatUnread,
                ),
                onClick = onMailClick,
            )
            if (!state.appUpdateDownloadUrl.isNullOrBlank()) {
                OverlayGameHudUpdateChip(
                    contentDescription = stringResource(R.string.overlay_hud_app_update_cd),
                    onClick = onAppUpdateClick,
                )
            }
        }
    }
}
