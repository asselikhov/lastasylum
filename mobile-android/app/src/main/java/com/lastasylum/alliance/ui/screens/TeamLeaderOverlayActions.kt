package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.overlay.OverlayTeamContextCache
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamJoinRequestDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.overlay.OverlayAwareAlertDialog
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.teamTagWithServerPrefix
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.launch

private fun isValidTeamTag3to4Letters(raw: String): Boolean {
    val t = raw.trim()
    var i = 0
    var count = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        if (!Character.isLetter(cp)) return false
        count++
        if (count > 4) return false
        i += Character.charCount(cp)
    }
    return count in 3..4
}

@Composable
fun TeamLeaderToolbar(
    team: TeamDetailDto,
    activeServerNumber: Int? = null,
    subtitle: String,
    isLeader: Boolean,
    pendingJoinRequests: Int,
    membersBusy: Boolean,
    editNameBusy: Boolean,
    overlayUi: Boolean,
    onAddMember: () -> Unit,
    onEditTeam: () -> Unit,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SquadRelayDimens.contentPaddingHorizontal,
                vertical = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = teamTagWithServerPrefix(team.tag.uppercase(), activeServerNumber),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = team.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isLeader) {
            IconButton(
                onClick = {
                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                    onAddMember()
                },
                enabled = !membersBusy,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonAdd,
                    contentDescription = stringResource(R.string.team_cd_add_member),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = {
                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                    onEditTeam()
                },
                enabled = !membersBusy && !editNameBusy,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.team_cd_edit_team_name),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (isLeader && pendingJoinRequests > 0) {
            BadgedBox(
                badge = {
                    Badge {
                        Text(if (pendingJoinRequests > 9) "9+" else "$pendingJoinRequests")
                    }
                },
            ) {
                IconButton(
                    onClick = {
                        OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                        onOpenInbox()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Inbox,
                        contentDescription = stringResource(R.string.profile_join_inbox_cd),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun TeamLeaderDialogsHost(
    teamId: String?,
    teamsRepository: TeamsRepository,
    showAddMember: Boolean,
    onShowAddMemberChange: (Boolean) -> Unit,
    showEditTeam: Boolean,
    onShowEditTeamChange: (Boolean) -> Unit,
    showJoinInbox: Boolean,
    onShowJoinInboxChange: (Boolean) -> Unit,
    editTeamNameDraft: String,
    onEditTeamNameDraftChange: (String) -> Unit,
    editTeamTagDraft: String,
    onEditTeamTagDraftChange: (String) -> Unit,
    addNameDraft: String,
    onAddNameDraftChange: (String) -> Unit,
    membersBusy: Boolean,
    onMembersBusyChange: (Boolean) -> Unit,
    editNameBusy: Boolean,
    onEditNameBusyChange: (Boolean) -> Unit,
    inboxBusy: Boolean,
    onInboxBusyChange: (Boolean) -> Unit,
    inboxRequests: List<TeamJoinRequestDto>,
    onInboxRequestsChange: (List<TeamJoinRequestDto>) -> Unit,
    inboxFeedback: String?,
    onInboxFeedbackChange: (String?) -> Unit,
    onReloadTeam: () -> Unit,
    onHudRefresh: (() -> Unit)? = null,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    val tid = teamId ?: return
    val reloadTeam = {
        OverlayTeamContextCache.invalidate()
        onReloadTeam()
    }

    if (showAddMember) {
        OverlayModalScope(preparedByCaller = true) {
            OverlayAwareAlertDialog(
                onDismissRequest = { if (!membersBusy) onShowAddMemberChange(false) },
                title = { Text(stringResource(R.string.team_add_member_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = addNameDraft,
                        onValueChange = onAddNameDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !membersBusy,
                        label = { Text(stringResource(R.string.team_add_member_hint)) },
                    )
                },
                confirmButton = {
                    Button(
                        enabled = !membersBusy && addNameDraft.trim().length >= 3,
                        onClick = {
                            val username = addNameDraft.trim()
                            if (username.length < 3) return@Button
                            scope.launch {
                                onMembersBusyChange(true)
                                teamsRepository.addMember(tid, username)
                                    .onSuccess {
                                        onAddNameDraftChange("")
                                        onShowAddMemberChange(false)
                                        reloadTeam()
                                    }
                                    .onFailure { e -> onError(e.toUserMessageRu(res)) }
                                onMembersBusyChange(false)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.team_add_member_btn))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!membersBusy) onShowAddMemberChange(false) }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }
    }

    if (showEditTeam) {
        OverlayModalScope(preparedByCaller = true) {
            OverlayAwareAlertDialog(
                onDismissRequest = { if (!editNameBusy) onShowEditTeamChange(false) },
                title = { Text(stringResource(R.string.team_edit_team_name_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editTeamNameDraft,
                            onValueChange = { onEditTeamNameDraftChange(it.take(48)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !editNameBusy,
                            label = { Text(stringResource(R.string.profile_field_team_full_name)) },
                        )
                        OutlinedTextField(
                            value = editTeamTagDraft,
                            onValueChange = { v ->
                                onEditTeamTagDraftChange(v.filter { it.isLetter() }.take(4))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !editNameBusy,
                            label = { Text(stringResource(R.string.profile_field_team_tag)) },
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !editNameBusy &&
                            editTeamNameDraft.trim().length >= 2 &&
                            isValidTeamTag3to4Letters(editTeamTagDraft),
                        onClick = {
                            val n = editTeamNameDraft.trim()
                            val tg = editTeamTagDraft.trim()
                            if (n.length < 2 || !isValidTeamTag3to4Letters(tg)) return@Button
                            scope.launch {
                                onEditNameBusyChange(true)
                                teamsRepository.updateTeamBranding(tid, n, tg)
                                    .onSuccess {
                                        onShowEditTeamChange(false)
                                        reloadTeam()
                                    }
                                    .onFailure { e -> onError(e.toUserMessageRu(res)) }
                                onEditNameBusyChange(false)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.profile_action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!editNameBusy) onShowEditTeamChange(false) }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }
    }

    if (showJoinInbox) {
        OverlayModalScope(preparedByCaller = true) {
            OverlayAwareAlertDialog(
                onDismissRequest = { if (!inboxBusy) onShowJoinInboxChange(false) },
                title = { Text(stringResource(R.string.profile_join_inbox_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                    ) {
                        inboxFeedback?.let { msg ->
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        if (inboxBusy && inboxRequests.isEmpty()) {
                            Text(stringResource(R.string.overlay_online_loading))
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(inboxRequests, key = { it.id }) { request ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = request.requesterUsername,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    onInboxBusyChange(true)
                                                    onInboxFeedbackChange(null)
                                                    teamsRepository.acceptJoinRequest(request.id)
                                                        .onSuccess {
                                                            onInboxFeedbackChange(
                                                                context.getString(R.string.profile_join_accept_ok),
                                                            )
                                                            teamsRepository.listPendingJoinRequests()
                                                                .onSuccess { onInboxRequestsChange(it) }
                                                            reloadTeam()
                                                            onHudRefresh?.invoke()
                                                        }
                                                        .onFailure { e ->
                                                            onInboxFeedbackChange(
                                                                e.toUserMessageRu(res).ifBlank {
                                                                    context.getString(R.string.profile_join_action_error)
                                                                },
                                                            )
                                                        }
                                                    onInboxBusyChange(false)
                                                }
                                            },
                                        ) {
                                            Text(stringResource(R.string.profile_join_accept))
                                        }
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    onInboxBusyChange(true)
                                                    onInboxFeedbackChange(null)
                                                    teamsRepository.rejectJoinRequest(request.id)
                                                        .onSuccess {
                                                            onInboxFeedbackChange(
                                                                context.getString(R.string.profile_join_reject_ok),
                                                            )
                                                            teamsRepository.listPendingJoinRequests()
                                                                .onSuccess { onInboxRequestsChange(it) }
                                                            onHudRefresh?.invoke()
                                                        }
                                                        .onFailure { e ->
                                                            onInboxFeedbackChange(
                                                                e.toUserMessageRu(res).ifBlank {
                                                                    context.getString(R.string.profile_join_action_error)
                                                                },
                                                            )
                                                        }
                                                    onInboxBusyChange(false)
                                                }
                                            },
                                        ) {
                                            Text(stringResource(R.string.profile_join_reject))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onShowJoinInboxChange(false) }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }
    }
}

/** Dialog + toolbar state holder for overlay team online panel. */
@Composable
fun rememberTeamLeaderOverlayState(): TeamLeaderOverlayUiState {
    return remember {
        TeamLeaderOverlayUiState()
    }
}

class TeamLeaderOverlayUiState {
    var showAddMember by mutableStateOf(false)
    var showEditTeam by mutableStateOf(false)
    var showJoinInbox by mutableStateOf(false)
    var addNameDraft by mutableStateOf("")
    var editTeamNameDraft by mutableStateOf("")
    var editTeamTagDraft by mutableStateOf("")
    var membersBusy by mutableStateOf(false)
    var editNameBusy by mutableStateOf(false)
    var inboxBusy by mutableStateOf(false)
    var inboxRequests by mutableStateOf<List<TeamJoinRequestDto>>(emptyList())
    var inboxFeedback by mutableStateOf<String?>(null)
}
