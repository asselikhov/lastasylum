package com.lastasylum.alliance.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ManageAccounts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.telegramDisplayHandle
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.launch

fun squadRoleCellLabel(context: Context, member: PlayerTeamMemberDto): String {
    return if (member.isLeader) {
        context.getString(R.string.team_squad_r5_label)
    } else {
        when (member.teamRole) {
            "R5" -> context.getString(R.string.team_squad_r5_label)
            "R4" -> context.getString(R.string.team_squad_r4_label)
            "R3" -> context.getString(R.string.team_squad_r3_label)
            "R2" -> context.getString(R.string.team_squad_r2_label)
            else -> context.getString(R.string.team_squad_r1_label)
        }
    }
}

@Composable
fun TeamTableHeader(showRoleEditColumn: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.team_col_player),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1.15f),
        )
        Text(
            text = stringResource(R.string.team_col_squad_role),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.72f),
        )
        Text(
            text = stringResource(R.string.team_col_telegram),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.95f),
        )
        if (showRoleEditColumn) {
            Spacer(Modifier.width(40.dp))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun TeamMemberTableRow(
    member: PlayerTeamMemberDto,
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
    val context = LocalContext.current
    val res = context.resources
    val avatar = telegramAvatarUrl(member.telegramUsername)
    val telegram = telegramDisplayHandle(member.telegramUsername)
    val roleLabel = squadRoleCellLabel(context, member)
    val canEditThisMemberRole =
        isSquadLeader && !member.isLeader && member.userId != currentUserId

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1.15f)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
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
                Text(
                    text = member.username,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = roleLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(0.72f)
                    .padding(horizontal = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = telegram ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(0.95f)
                    .padding(end = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSquadLeader) {
                Box(
                    modifier = Modifier.width(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (canEditThisMemberRole) {
                        IconButton(
                            onClick = { onRequestEditMemberRole(member) },
                            enabled = !busy,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ManageAccounts,
                                contentDescription = stringResource(R.string.team_edit_member_role_cd),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        if (isSquadLeader && !member.isLeader && member.userId != currentUserId) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
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
                ) {
                    Text(
                        text = stringResource(R.string.team_remove_member),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.width(2.dp))
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
