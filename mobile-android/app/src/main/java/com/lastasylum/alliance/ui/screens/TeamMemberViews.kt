package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import com.lastasylum.alliance.overlay.OverlayAwareAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import com.lastasylum.alliance.data.voice.TeamVoicePresenceStore
import com.lastasylum.alliance.data.voice.VoicePeerState
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.ui.team.TeamVoiceRosterPresenceBinding
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.toUserMessageRu
import com.lastasylum.alliance.ui.util.formatPresenceTimestampRu
import com.lastasylum.alliance.ui.util.isOverlayIngameNow
import java.util.Locale
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
private fun squadRoleSectionTitle(roleCode: String): String = roleCode

@OptIn(ExperimentalMaterial3Api::class)
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
    var searchQuery by remember { mutableStateOf("") }
    val filteredMembers = remember(members, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            members
        } else {
            members.filter { m ->
                m.username.lowercase().contains(q) ||
                    (m.telegramUsername?.lowercase()?.contains(q) == true)
            }
        }
    }
    val grouped = remember(filteredMembers) { groupMembersBySquadRole(filteredMembers) }
    var expandedRoles by remember { mutableStateOf(setOf<String>()) }
    val voicePeers by TeamVoicePresenceStore.peers.collectAsStateWithLifecycle()
    val overlayVisible by CombatOverlayService.overlayVisible.collectAsStateWithLifecycle()

    TeamVoiceRosterPresenceBinding(
        active = true,
        overlayVisible = overlayVisible,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxSize()
            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
    ) {
        TeamMembersCompactSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
        ) {
            grouped.forEach { (roleCode, subMembers) ->
                val expanded = roleCode in expandedRoles
                item(key = "hdr-$roleCode") {
                    SquadRoleSectionHeader(
                        roleCode = roleCode,
                        sectionTitle = squadRoleSectionTitle(roleCode),
                        memberCount = subMembers.size,
                        expanded = expanded,
                        onToggle = {
                            expandedRoles =
                                if (roleCode in expandedRoles) {
                                    expandedRoles - roleCode
                                } else {
                                    expandedRoles + roleCode
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
                            voicePeer = voicePeers[member.userId],
                            overlayVisible = overlayVisible,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMembersCompactSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholder = stringResource(R.string.team_members_search_hint)
    val clearCd = stringResource(R.string.team_members_search_clear_cd)
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = clearCd,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SquadRoleSectionHeader(
    roleCode: String,
    sectionTitle: String,
    memberCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val sectionToggleCd = stringResource(R.string.team_role_section_toggle_cd, roleCode)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = sectionToggleCd }
                .clickable(
                    onClick = onToggle,
                    interactionSource = interaction,
                    indication = ripple(bounded = true),
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
    }
}

@Composable
private fun TeamMemberVoiceBadges(
    micOn: Boolean,
    soundOn: Boolean,
    modifier: Modifier = Modifier,
) {
    val micCd = stringResource(
        if (micOn) R.string.team_member_voice_mic_on_cd else R.string.team_member_voice_mic_off_cd,
    )
    val soundCd = stringResource(
        if (soundOn) R.string.team_member_voice_sound_on_cd else R.string.team_member_voice_sound_off_cd,
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamMemberVoiceBadge(
            iconRes = if (micOn) R.drawable.ic_overlay_mic_on else R.drawable.ic_overlay_mic_off,
            contentDescription = micCd,
            accent = Color(0xFF2E7D32),
            accentGlow = Color(0xFF81C784),
            active = micOn,
        )
        TeamMemberVoiceBadge(
            iconRes = if (soundOn) R.drawable.ic_overlay_volume_on else R.drawable.ic_overlay_volume_off,
            contentDescription = soundCd,
            accent = Color(0xFF1565C0),
            accentGlow = Color(0xFF64B5F6),
            active = soundOn,
        )
    }
}

@Composable
private fun TeamMemberVoiceBadge(
    iconRes: Int,
    contentDescription: String,
    accent: Color,
    accentGlow: Color,
    active: Boolean,
) {
    val idleBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val idleBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (active) {
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.92f),
                            accent.copy(alpha = 0.72f),
                        ),
                    )
                } else {
                    Brush.linearGradient(listOf(idleBg, idleBg))
                },
            )
            .border(
                width = 1.dp,
                color = if (active) accentGlow.copy(alpha = 0.55f) else idleBorder,
                shape = RoundedCornerShape(7.dp),
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    voicePeer: VoicePeerState?,
    overlayVisible: Boolean,
) {
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    val avatar = telegramAvatarUrl(member.telegramUsername)
    val unknownPresence = stringResource(R.string.team_member_last_in_game_unknown)
    val lastInGameTemplate = stringResource(R.string.team_member_last_in_game_template)
    val lastInGameLine = remember(member.lastPresenceAt, unknownPresence, lastInGameTemplate) {
        val raw = formatPresenceTimestampRu(member.lastPresenceAt)
        val slot = raw.ifBlank { unknownPresence }
        String.format(Locale.getDefault(), lastInGameTemplate, slot)
    }
    val inGameNow = remember(member.presenceStatus, member.lastPresenceAt) {
        isOverlayIngameNow(member.presenceStatus, member.lastPresenceAt)
    }
    val voiceMicOn = voicePeer?.micOn == true
    val voiceSoundOn = voicePeer?.soundOn == true
    val inGameCd = stringResource(R.string.team_member_in_game_cd)
    val notInGameCd = stringResource(R.string.team_member_not_in_game_cd)
    val canEditThisMemberRole =
        isSquadLeader && !member.isLeader && member.userId != currentUserId
    val canRemove = isSquadLeader && !member.isLeader && member.userId != currentUserId
    val canManage = canEditThisMemberRole || canRemove
    var removeConfirmTarget by remember(member.userId) { mutableStateOf(false) }
    var showActionsSheet by remember(member.userId) { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = member.username,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (inGameNow && overlayVisible) {
                        TeamMemberVoiceBadges(
                            micOn = voiceMicOn,
                            soundOn = voiceSoundOn,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = if (inGameNow) inGameCd else notInGameCd,
                        modifier = Modifier.size(10.dp),
                        tint = if (inGameNow) {
                            Color(0xFF2E7D32)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        },
                    )
                    Text(
                        text = lastInGameLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (canManage) {
                IconButton(
                    onClick = { showActionsSheet = true },
                    enabled = !busy,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.team_member_actions_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showActionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showActionsSheet = false },
            sheetState = sheetState,
            containerColor = SquadRelaySurfaces.dialogColor(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.team_member_actions_title, member.username),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                if (canEditThisMemberRole) {
                    Button(
                        onClick = {
                            showActionsSheet = false
                            onRequestEditMemberRole(member)
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.team_member_action_change_role))
                    }
                }
                if (canRemove) {
                    OutlinedButton(
                        onClick = {
                            showActionsSheet = false
                            removeConfirmTarget = true
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PersonRemove,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                stringResource(R.string.team_remove_member),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (removeConfirmTarget) {
        OverlayAwareAlertDialog(
            onDismissRequest = { if (!busy) removeConfirmTarget = false },
            title = { Text(stringResource(R.string.team_remove_member_confirm_title)) },
            text = {
                Text(stringResource(R.string.team_remove_member_confirm_body, member.username))
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        removeConfirmTarget = false
                        scope.launch {
                            onBusyChange(true)
                            teamsRepository.removeMember(teamId, member.userId)
                                .onSuccess { onReload() }
                                .onFailure { e -> onError(e.toUserMessageRu(res)) }
                            onBusyChange(false)
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.team_remove_member),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { removeConfirmTarget = false },
                ) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
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
    OverlayAwareAlertDialog(
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
