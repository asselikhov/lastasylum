package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.telegramDisplayHandle
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.launch

private val squadRoleOrder = listOf("R5", "R4", "R3", "R2", "R1")

fun squadRoleCode(member: PlayerTeamMemberDto): String {
    val raw = member.teamRole.trim().uppercase().takeIf { it.isNotEmpty() } ?: "R1"
    return if (member.isLeader) "R5" else raw
}

private fun groupMembersBySquadRole(
    members: List<PlayerTeamMemberDto>,
): List<Pair<String, List<PlayerTeamMemberDto>>> {
    val byRole = members.groupBy { squadRoleCode(it) }
    return squadRoleOrder.mapNotNull { code ->
        val list = byRole[code]?.sortedBy { it.username.lowercase() }
        if (list.isNullOrEmpty()) null else code to list
    }
}

@Composable
private fun squadRoleSubtitle(roleCode: String): String {
    return when (roleCode) {
        "R5" -> stringResource(R.string.team_squad_r5_label)
        "R4" -> stringResource(R.string.team_squad_r4_label)
        "R3" -> stringResource(R.string.team_squad_r3_label)
        "R2" -> stringResource(R.string.team_squad_r2_label)
        else -> stringResource(R.string.team_squad_r1_label)
    }
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
    val grouped = remember(members) { groupMembersBySquadRole(members) }
    var collapsedRoles by remember(members) { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouped.forEach { (roleCode, subMembers) ->
            val expanded = roleCode !in collapsedRoles
            item(key = "hdr-$roleCode") {
                SquadRoleSectionHeader(
                    roleCode = roleCode,
                    memberCount = subMembers.size,
                    expanded = expanded,
                    onToggle = {
                        collapsedRoles =
                            if (roleCode in collapsedRoles) {
                                collapsedRoles - roleCode
                            } else {
                                collapsedRoles + roleCode
                            }
                    },
                )
            }
            if (expanded) {
                items(
                    subMembers,
                    key = { "${roleCode}_${it.userId}" },
                ) { member ->
                    SquadMemberCard(
                        member = member,
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
                }
            }
        }
    }
}

@Composable
private fun SquadRoleSectionHeader(
    roleCode: String,
    memberCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val sectionToggleCd = stringResource(R.string.team_role_section_toggle_cd, roleCode)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = sectionToggleCd }
                .clickable(
                    onClick = onToggle,
                    interactionSource = interaction,
                    indication = ripple(bounded = true),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = roleCode,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = memberCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = squadRoleSubtitle(roleCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SquadMemberCard(
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
    val res = LocalContext.current.resources
    val avatar = telegramAvatarUrl(member.telegramUsername)
    val tgHandle = telegramDisplayHandle(member.telegramUsername)
    val tgPlaceholder = stringResource(R.string.team_tg_not_set)
    val tgLine = stringResource(R.string.team_tg_line, tgHandle ?: tgPlaceholder)
    val canEditThisMemberRole =
        isSquadLeader && !member.isLeader && member.userId != currentUserId
    val canRemove = isSquadLeader && !member.isLeader && member.userId != currentUserId

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
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
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = member.username.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = member.username,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tgLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSquadLeader) {
                Row(
                    horizontalArrangement = Arrangement.End,
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
                    }
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
