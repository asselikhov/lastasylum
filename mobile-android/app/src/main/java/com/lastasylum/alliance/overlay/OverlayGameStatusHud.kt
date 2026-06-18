package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.padding
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
    if (!state.appUpdateDownloadUrl.isNullOrBlank()) {
        OverlayAppUpdateGateBar(
            onUpdateClick = onAppUpdateClick,
            modifier = modifier.padding(
                top = HudBadgeOverflowPaddingTop,
                end = HudBadgeOverflowPaddingEnd,
            ),
        )
        return
    }
    OverlayGameHudBar(modifier = modifier) {
        OverlayGameHudChipRow {
            OverlayGameHudChip(
                icon = OverlayHudIcons.news,
                accent = OverlayHudChipAccent.News,
                badgeCount = state.teamNewsUnread,
                contentDescription = stringResource(R.string.overlay_hud_news_cd, state.teamNewsUnread),
                onClick = onNewsClick,
            )
            OverlayGameHudChip(
                icon = OverlayHudIcons.forum,
                accent = OverlayHudChipAccent.Forum,
                badgeCount = state.forumUnread,
                contentDescription = stringResource(R.string.overlay_hud_forum_cd, state.forumUnread),
                onClick = onForumClick,
            )
            OverlayGameHudChip(
                icon = OverlayHudIcons.mail,
                accent = OverlayHudChipAccent.Mail,
                badgeCount = state.allianceChatUnread,
                contentDescription = stringResource(
                    R.string.overlay_hud_alliance_chat_cd,
                    state.allianceChatUnread,
                ),
                onClick = onMailClick,
            )
        }
    }
}
