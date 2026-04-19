package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = SquadRelayDimens.contentPaddingHorizontal,
                                vertical = 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        stringResource(R.string.team_col_player),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1.2f),
                                    )
                                    Text(
                                        stringResource(R.string.team_col_role),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(0.7f),
                                    )
                                    if (isLeader) {
                                        Text(
                                            stringResource(R.string.team_col_actions),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(0.9f),
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            items(d.members, key = { it.userId }) { m ->
                                TeamMemberTableRow(
                                    member = m,
                                    isLeader = isLeader,
                                    currentUserId = currentUserId,
                                    teamId = teamId,
                                    busy = busy,
                                    onBusyChange = { busy = it },
                                    onReload = { reload() },
                                    teamsRepository = teamsRepository,
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            if (isLeader) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.team_add_member_title),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            OutlinedTextField(
                                                value = addNameDraft,
                                                onValueChange = { addNameDraft = it },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                enabled = !busy,
                                                label = { Text(stringResource(R.string.team_add_member_hint)) },
                                            )
                                            Button(
                                                onClick = {
                                                    val u = addNameDraft.trim()
                                                    if (u.length < 3) return@Button
                                                    scope.launch {
                                                        busy = true
                                                        teamsRepository.addMember(teamId, u)
                                                            .onSuccess {
                                                                addNameDraft = ""
                                                                reload()
                                                            }
                                                            .onFailure { e ->
                                                                error = e.toUserMessageRu(res)
                                                            }
                                                        busy = false
                                                    }
                                                },
                                                enabled = !busy && addNameDraft.trim().length >= 3,
                                            ) {
                                                Text(stringResource(R.string.team_add_member_btn))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMemberTableRow(
    member: PlayerTeamMemberDto,
    isLeader: Boolean,
    currentUserId: String,
    teamId: String,
    busy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onReload: () -> Unit,
    teamsRepository: TeamsRepository,
) {
    val scope = rememberCoroutineScope()
    val roleLabel = if (member.isLeader) {
        stringResource(R.string.team_role_leader)
    } else {
        stringResource(R.string.team_role_member)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = member.username,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = roleLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isLeader) {
            Row(
                modifier = Modifier.weight(0.9f),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!member.isLeader && member.userId != currentUserId) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                onBusyChange(true)
                                teamsRepository.removeMember(teamId, member.userId)
                                    .onSuccess { onReload() }
                                    .onFailure { /* silent; row refresh on success */ }
                                onBusyChange(false)
                            }
                        },
                        enabled = !busy,
                    ) {
                        Text(
                            stringResource(R.string.team_remove_member),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    }
}
