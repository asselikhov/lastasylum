package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.telegramDisplayHandle
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.launch

private object SquadTableTokens {
    val avatarCol: Dp = 44.dp
    val roleCol: Dp = 52.dp
    val telegramCol: Dp = 152.dp
    /** Manage role + remove member for leader */
    val actionCol: Dp = 104.dp
    val rowHeight: Dp = 56.dp
    val cellPadH: Dp = 8.dp
    val cellPadV: Dp = 6.dp
}

/** Width so the longest nickname in the roster fits on one line (approx. glyph width). */
fun squadNicknameColumnWidth(members: List<PlayerTeamMemberDto>): Dp {
    val longest = members.maxOfOrNull { it.username.length }?.coerceIn(1, 72) ?: 6
    return (longest * 13 + 36).dp.coerceIn(112.dp, 400.dp)
}

fun squadRoleCode(member: PlayerTeamMemberDto): String {
    val raw = member.teamRole.trim().uppercase().takeIf { it.isNotEmpty() } ?: "R1"
    return if (member.isLeader) "R5" else raw
}

@Composable
fun SquadTeamRoster(
    members: List<PlayerTeamMemberDto>,
    isSquadLeader: Boolean,
    currentUserId: String,
    teamId: String,
    busy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onReload: () -> Unit,
    onError: (String?) -> Unit,
    teamsRepository: TeamsRepository,
    onRequestEditMemberRole: (PlayerTeamMemberDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nickW = remember(members) { squadNicknameColumnWidth(members) }
    val actionW = if (isSquadLeader) SquadTableTokens.actionCol else 0.dp
    val tableInnerWidth =
        SquadTableTokens.avatarCol + nickW + SquadTableTokens.roleCol +
            SquadTableTokens.telegramCol + actionW + SquadTableTokens.cellPadH * 2
    val hScroll = rememberScrollState()

    BoxWithConstraints(modifier = modifier) {
        val useScroll = tableInnerWidth > maxWidth
        val columnWidth = maxOf(tableInnerWidth, maxWidth)

        @Composable
        fun rosterColumn(modifierForColumn: Modifier) {
            Column(
                modifier = modifierForColumn
                    .width(columnWidth)
                    .fillMaxHeight(),
            ) {
                TeamTableHeader(
                    nicknameColumnWidth = nickW,
                    showRoleEditColumn = isSquadLeader,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(members, key = { it.userId }) { member ->
                        TeamMemberTableRow(
                            member = member,
                            nicknameColumnWidth = nickW,
                            isSquadLeader = isSquadLeader,
                            currentUserId = currentUserId,
                            teamId = teamId,
                            busy = busy,
                            onBusyChange = onBusyChange,
                            onReload = onReload,
                            onError = onError,
                            teamsRepository = teamsRepository,
                            onRequestEditMemberRole = onRequestEditMemberRole,
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }

        if (useScroll) {
            Row(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll),
            ) {
                rosterColumn(Modifier)
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                rosterColumn(Modifier)
            }
        }
    }
}

@Composable
fun TeamTableHeader(
    nicknameColumnWidth: Dp,
    showRoleEditColumn: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SquadTableTokens.rowHeight)
            .padding(horizontal = SquadTableTokens.cellPadH, vertical = SquadTableTokens.cellPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(SquadTableTokens.avatarCol))
        Text(
            text = stringResource(R.string.team_col_player),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(nicknameColumnWidth),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = stringResource(R.string.team_col_squad_role),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(SquadTableTokens.roleCol),
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.team_col_telegram),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(SquadTableTokens.telegramCol),
            maxLines = 1,
        )
        if (showRoleEditColumn) {
            Spacer(Modifier.width(SquadTableTokens.actionCol))
        }
    }
}

@Composable
fun TeamMemberTableRow(
    member: PlayerTeamMemberDto,
    nicknameColumnWidth: Dp,
    isSquadLeader: Boolean,
    currentUserId: String,
    teamId: String,
    busy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onReload: () -> Unit,
    onError: (String?) -> Unit,
    teamsRepository: TeamsRepository,
    onRequestEditMemberRole: (PlayerTeamMemberDto) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    val avatar = telegramAvatarUrl(member.telegramUsername)
    val telegram = telegramDisplayHandle(member.telegramUsername)
    val roleCode = squadRoleCode(member)
    val canEditThisMemberRole =
        isSquadLeader && !member.isLeader && member.userId != currentUserId
    val canRemove = isSquadLeader && !member.isLeader && member.userId != currentUserId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SquadTableTokens.rowHeight)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = SquadTableTokens.cellPadH, vertical = SquadTableTokens.cellPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = member.username.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(SquadTableTokens.avatarCol - 36.dp))
            Box(
                modifier = Modifier.width(nicknameColumnWidth),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = member.username,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = roleCode,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(SquadTableTokens.roleCol),
            )
            Text(
                text = telegram ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(SquadTableTokens.telegramCol),
            )
            if (isSquadLeader) {
                Row(
                    modifier = Modifier.width(SquadTableTokens.actionCol),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canEditThisMemberRole) {
                        IconButton(
                            onClick = { onRequestEditMemberRole(member) },
                            enabled = !busy,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ManageAccounts,
                                contentDescription = stringResource(R.string.team_edit_member_role_cd),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Spacer(Modifier.width(44.dp))
                    }
                    if (canRemove) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    onBusyChange(true)
                                    teamsRepository.removeMember(teamId, member.userId)
                                        .onSuccess { onReload() }
                                        .onFailure { e -> onError(e.toUserMessageRu(res)) }
                                    onBusyChange(false)
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PersonRemove,
                                contentDescription = stringResource(R.string.team_remove_member_cd),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Spacer(Modifier.width(44.dp))
                    }
                }
            }
    }
}

private data class SquadRoleOption(val code: String, val titleRes: Int)

@Composable
fun SquadMemberRoleEditDialog(
    member: PlayerTeamMemberDto,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    teamId: String,
    teamsRepository: TeamsRepository,
    onError: (String?) -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    val options = listOf(
        SquadRoleOption("R4", R.string.team_squad_pick_r4),
        SquadRoleOption("R3", R.string.team_squad_pick_r3),
        SquadRoleOption("R2", R.string.team_squad_pick_r2),
        SquadRoleOption("R1", R.string.team_squad_pick_r1),
    )
    val initialCode = member.teamRole.takeIf { it != "R5" } ?: "R1"
    val initial = if (options.any { it.code == initialCode }) initialCode else "R1"
    var selected by remember(member.userId) { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                stringResource(R.string.team_edit_role_title, member.username),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { opt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == opt.code,
                                onClick = { if (!saving) selected = opt.code },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == opt.code,
                            onClick = null,
                            enabled = !saving,
                        )
                        Text(
                            text = stringResource(opt.titleRes),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        teamsRepository.updateMemberSquadRole(teamId, member.userId, selected)
                            .onSuccess {
                                onSaved()
                                onDismiss()
                            }
                            .onFailure { e -> onError(e.toUserMessageRu(res)) }
                        saving = false
                    }
                },
                enabled = !saving,
            ) {
                Text(stringResource(R.string.profile_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!saving) onDismiss() }) {
                Text(stringResource(R.string.profile_action_cancel))
            }
        },
    )
}
