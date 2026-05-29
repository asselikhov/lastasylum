package com.lastasylum.alliance.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.ui.components.OverlayMemberVoiceBadges
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.util.formatOverlayPresenceAgeRu
import com.lastasylum.alliance.ui.util.telegramAvatarUrl

enum class OverlayOnlineMemberCellMode {
    Presence,
    Selectable,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayOnlineMemberGridCell(
    member: OverlayOnlineMemberUiModel,
    micOn: Boolean,
    soundOn: Boolean,
    modifier: Modifier = Modifier,
    mode: OverlayOnlineMemberCellMode = OverlayOnlineMemberCellMode.Presence,
    selected: Boolean = false,
    selfLabel: String = stringResource(R.string.overlay_online_self),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onToggleSelect: (() -> Unit)? = null,
) {
    val tokens = OverlayOnlineMemberTokens
    val squadRole = member.teamRole
    val roleColor = roleAccentColor(squadRole)
    val roleCd = stringResource(R.string.overlay_member_squad_rank_cd, squadRole)
    val displayName = if (member.isSelf) {
        "${member.username} ($selfLabel)"
    } else {
        member.username
    }
    val presenceAge = formatOverlayPresenceAgeRu(member.lastPresenceAt)
    val borderColor = when {
        mode == OverlayOnlineMemberCellMode.Selectable && selected -> tokens.borderLive
        member.inGameNow && member.freshness == PresenceFreshness.StaleSoon -> tokens.borderStaleSoon
        member.inGameNow -> tokens.borderLive
        else -> tokens.borderRecent
    }
    val fillColor = if (member.inGameNow && member.freshness == PresenceFreshness.StaleSoon) {
        tokens.glassFillStale
    } else {
        tokens.glassFill
    }
    val statusText = when {
        member.inGameNow -> stringResource(R.string.overlay_online_status_ingame)
        else -> stringResource(R.string.overlay_online_status_recent)
    }
    val statusLine = if (presenceAge.isNotBlank()) "$statusText · $presenceAge" else statusText
    val rowCd = if (mode == OverlayOnlineMemberCellMode.Selectable) {
        stringResource(
            if (selected) {
                R.string.overlay_reactions_member_selected_cd
            } else {
                R.string.overlay_reactions_member_row_cd
            },
            member.username,
            squadRole,
        )
    } else {
        displayName
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = tokens.cellMinHeight)
            .semantics { contentDescription = rowCd }
            .clip(tokens.cellShape)
            .background(
                Brush.verticalGradient(
                    listOf(fillColor, fillColor.copy(alpha = fillColor.alpha * 0.85f)),
                ),
            )
            .border(1.dp, borderColor, tokens.cellShape)
            .then(
                when (mode) {
                    OverlayOnlineMemberCellMode.Selectable -> Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onToggleSelect?.invoke() },
                        onLongClick = null,
                    )
                    OverlayOnlineMemberCellMode.Presence -> Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = onClick != null || onLongClick != null,
                        onClick = { onClick?.invoke() },
                        onLongClick = { onLongClick?.invoke() },
                    )
                },
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (mode == OverlayOnlineMemberCellMode.Selectable) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect?.invoke() },
                    modifier = Modifier.size(28.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = tokens.borderLive,
                        uncheckedColor = tokens.metaColor,
                        checkmarkColor = Color.White,
                    ),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    MemberAvatar(
                        username = member.username,
                        telegramUsername = member.telegramUsername,
                        inGameNow = member.inGameNow,
                        freshness = member.freshness,
                    )
                    RoleChip(
                        role = squadRole,
                        color = roleColor,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 6.dp)
                            .semantics { contentDescription = roleCd },
                    )
                }
                Text(
                    text = displayName,
                    style = tokens.titleStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            text = statusLine,
            style = tokens.metaStyle.copy(
                color = if (member.inGameNow) tokens.metaColor else tokens.mutedColor,
            ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (member.inGameNow && mode == OverlayOnlineMemberCellMode.Presence) {
            AnimatedVisibility(
                visible = micOn || soundOn,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                OverlayMemberVoiceBadges(micOn = micOn, soundOn = soundOn)
            }
        }
        if (member.inGameNow && member.freshness == PresenceFreshness.StaleSoon) {
            Text(
                text = stringResource(R.string.overlay_online_stale_soon),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.borderStaleSoon,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun OverlayOnlineMemberGridCellFromDto(
    member: PlayerTeamMemberDto,
    micOn: Boolean,
    soundOn: Boolean,
    modifier: Modifier = Modifier,
    mode: OverlayOnlineMemberCellMode = OverlayOnlineMemberCellMode.Selectable,
    selected: Boolean = false,
    selfUserId: String? = null,
    onToggleSelect: (() -> Unit)? = null,
) {
    val uiModel = remember(member.userId, member.presenceStatus, member.lastPresenceAt) {
        val inGame = com.lastasylum.alliance.ui.util.isOverlayIngameNow(
            member.presenceStatus,
            member.lastPresenceAt,
        )
        OverlayOnlineMemberUiModel(
            userId = member.userId,
            username = member.username,
            telegramUsername = member.telegramUsername,
            teamRole = member.teamRole.trim().uppercase().ifBlank { "R1" },
            isLeader = member.isLeader,
            presenceStatus = member.presenceStatus,
            lastPresenceAt = member.lastPresenceAt,
            isSelf = !selfUserId.isNullOrBlank() && member.userId == selfUserId,
            inGameNow = inGame,
            freshness = if (inGame) presenceFreshness(member.lastPresenceAt) else PresenceFreshness.Stale,
        )
    }
    OverlayOnlineMemberGridCell(
        member = uiModel,
        micOn = micOn,
        soundOn = soundOn,
        modifier = modifier,
        mode = mode,
        selected = selected,
        onToggleSelect = onToggleSelect,
    )
}

@Composable
private fun MemberAvatar(
    username: String,
    telegramUsername: String?,
    inGameNow: Boolean,
    freshness: PresenceFreshness,
) {
    val tokens = OverlayOnlineMemberTokens
    val avatarUrl = telegramAvatarUrl(telegramUsername)
    val letter = username.trim().take(1).uppercase().ifBlank { "?" }
    val ringColor = when {
        inGameNow && freshness == PresenceFreshness.StaleSoon -> tokens.borderStaleSoon
        inGameNow -> tokens.livePulse
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(tokens.avatarOuter)
            .then(
                if (inGameNow) {
                    Modifier
                        .clip(CircleShape)
                        .background(ringColor.copy(alpha = 0.28f))
                        .padding(tokens.avatarRingWidth)
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
                .background(Color(0xFF1E2A3A)),
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
                    style = tokens.titleStyle.copy(color = tokens.metaColor),
                )
            }
        }
    }
}

@Composable
private fun RoleChip(
    role: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = OverlayOnlineMemberTokens
    Text(
        text = role,
        style = tokens.chipStyle.copy(color = color),
        modifier = modifier
            .clip(RoundedCornerShape(tokens.chipRadius))
            .background(color.copy(alpha = 0.14f))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(tokens.chipRadius))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}
