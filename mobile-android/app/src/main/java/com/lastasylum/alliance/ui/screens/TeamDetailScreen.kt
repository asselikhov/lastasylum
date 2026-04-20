package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.launch

private fun isValidThreeLetterTeamTag(raw: String): Boolean {
    val t = raw.trim()
    var i = 0
    var count = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        if (!Character.isLetter(cp)) return false
        count++
        if (count > 3) return false
        i += Character.charCount(cp)
    }
    return count == 3
}

@Composable
fun TeamDetailScreen(
    teamId: String,
    currentUserId: String,
    teamsRepository: TeamsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<TeamDetailDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addNameDraft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showEditTeamNameDialog by remember { mutableStateOf(false) }
    var editTeamNameDraft by remember { mutableStateOf("") }
    var editTeamTagDraft by remember { mutableStateOf("") }
    var editNameBusy by remember { mutableStateOf(false) }
    var roleEditMember by remember { mutableStateOf<PlayerTeamMemberDto?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            teamsRepository.getTeam(teamId)
                .onSuccess {
                    detail = it
                    loading = false
                }
                .onFailure { e ->
                    error = e.toUserMessageRu(res)
                    loading = false
                }
        }
    }

    LaunchedEffect(teamId) {
        loading = true
        error = null
        teamsRepository.getTeam(teamId)
            .onSuccess {
                detail = it
                loading = false
            }
            .onFailure { e ->
                error = e.toUserMessageRu(res)
                loading = false
            }
    }

    val isLeader = detail?.leaderUserId == currentUserId

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.team_detail_back_cd),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = detail?.let { t ->
                            stringResource(R.string.team_detail_title_fmt, t.displayName, t.tag)
                        } ?: stringResource(R.string.team_detail_loading_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isLeader && detail != null) {
                    IconButton(
                        onClick = {
                            addNameDraft = ""
                            showAddMemberDialog = true
                        },
                        enabled = !busy,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = stringResource(R.string.team_cd_add_member),
                        )
                    }
                    IconButton(
                        onClick = {
                            editTeamNameDraft = detail!!.displayName.trim()
                            editTeamTagDraft = detail!!.tag.trim()
                            showEditTeamNameDialog = true
                        },
                        enabled = !busy && !editNameBusy,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.team_cd_edit_team_name),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    loading && detail == null -> {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                    error != null && detail == null -> {
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(SquadRelayDimens.contentPaddingHorizontal),
                        )
                    }
                    detail != null -> {
                        val d = detail!!
                        SquadTeamRoster(
                            members = d.members,
                            isSquadLeader = isLeader,
                            currentUserId = currentUserId,
                            teamId = teamId,
                            busy = busy,
                            onBusyChange = { busy = it },
                            onReload = { reload() },
                            onError = { msg -> error = msg },
                            teamsRepository = teamsRepository,
                            onRequestEditMemberRole = { x -> roleEditMember = x },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                                    vertical = 12.dp,
                                ),
                        )
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showAddMemberDialog = false },
            title = { Text(stringResource(R.string.team_add_member_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = addNameDraft,
                    onValueChange = { addNameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.team_add_member_hint)) },
                )
            },
            confirmButton = {
                Button(
                    enabled = !busy && addNameDraft.trim().length >= 3,
                    onClick = {
                        val u = addNameDraft.trim()
                        if (u.length < 3) return@Button
                        scope.launch {
                            busy = true
                            teamsRepository.addMember(teamId, u)
                                .onSuccess {
                                    addNameDraft = ""
                                    showAddMemberDialog = false
                                    reload()
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            busy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.team_add_member_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!busy) showAddMemberDialog = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    if (showEditTeamNameDialog) {
        AlertDialog(
            onDismissRequest = { if (!editNameBusy) showEditTeamNameDialog = false },
            title = { Text(stringResource(R.string.team_edit_team_name_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editTeamNameDraft,
                        onValueChange = { editTeamNameDraft = it.take(48) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !editNameBusy,
                        label = { Text(stringResource(R.string.profile_field_team_full_name)) },
                    )
                    OutlinedTextField(
                        value = editTeamTagDraft,
                        onValueChange = { v ->
                            editTeamTagDraft = v.filter { it.isLetter() }.take(3)
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
                        isValidThreeLetterTeamTag(editTeamTagDraft),
                    onClick = {
                        val n = editTeamNameDraft.trim()
                        val tg = editTeamTagDraft.trim()
                        if (n.length < 2 || !isValidThreeLetterTeamTag(tg)) return@Button
                        scope.launch {
                            editNameBusy = true
                            teamsRepository.updateTeamBranding(teamId, n, tg)
                                .onSuccess {
                                    showEditTeamNameDialog = false
                                    reload()
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            editNameBusy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.profile_action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!editNameBusy) showEditTeamNameDialog = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    roleEditMember?.let { member ->
        SquadMemberRoleEditDialog(
            member = member,
            onDismiss = { roleEditMember = null },
            onSaved = { reload() },
            teamId = teamId,
            teamsRepository = teamsRepository,
            onError = { msg -> error = msg },
        )
    }
}
