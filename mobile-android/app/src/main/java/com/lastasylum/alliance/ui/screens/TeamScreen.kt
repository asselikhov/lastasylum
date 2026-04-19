package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamJoinRequestDto
import com.lastasylum.alliance.data.teams.TeamSearchResultDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.telegramDisplayHandle
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.delay
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
fun TeamScreen(
    currentUserId: String,
    teamsRepository: TeamsRepository,
) {
    val context = LocalContext.current
    val app = remember { AppContainer.from(context.applicationContext) }
    val res = context.resources
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<MyProfileDto?>(null) }
    var teamDetail by remember { mutableStateOf<TeamDetailDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showJoinInbox by remember { mutableStateOf(false) }

    var createName by remember { mutableStateOf("") }
    var createTag by remember { mutableStateOf("") }
    var createBusy by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    var joinSearch by remember { mutableStateOf("") }
    var joinResults by remember { mutableStateOf<List<TeamSearchResultDto>>(emptyList()) }
    var joinSearchBusy by remember { mutableStateOf(false) }
    var joinActionBusy by remember { mutableStateOf(false) }
    var joinFeedback by remember { mutableStateOf<String?>(null) }

    var inboxRequests by remember { mutableStateOf<List<TeamJoinRequestDto>>(emptyList()) }
    var inboxBusy by remember { mutableStateOf(false) }
    var membersBusy by remember { mutableStateOf(false) }
    var addNameDraft by remember { mutableStateOf("") }

    fun reloadProfileAndTeam() {
        scope.launch {
            loading = true
            error = null
            app.usersRepository.getMyProfile()
                .onSuccess { my ->
                    profile = my
                    val teamId = my.playerTeamId
                    if (teamId.isNullOrBlank()) {
                        teamDetail = null
                    } else {
                        teamsRepository.getTeam(teamId)
                            .onSuccess { detail -> teamDetail = detail }
                            .onFailure { e ->
                                teamDetail = null
                                error = e.toUserMessageRu(res)
                            }
                    }
                }
                .onFailure {
                    error = context.getString(R.string.profile_load_error)
                    profile = null
                    teamDetail = null
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        reloadProfileAndTeam()
    }

    LaunchedEffect(joinSearch) {
        delay(400)
        val q = joinSearch.trim()
        if (q.length < 1) {
            joinResults = emptyList()
            return@LaunchedEffect
        }
        joinSearchBusy = true
        teamsRepository.searchTeams(q)
            .onSuccess { joinResults = it }
            .onFailure { joinResults = emptyList() }
        joinSearchBusy = false
    }

    val isLeader = profile?.isPlayerTeamLeader == true
    val pending = profile?.pendingPlayerTeamJoinRequests ?: 0

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                    vertical = SquadRelayDimens.screenTopPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.team_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (isLeader && pending > 0) {
                    BadgedBox(
                        badge = {
                            Badge {
                                Text(if (pending > 9) "9+" else "$pending")
                            }
                        },
                    ) {
                        IconButton(
                            onClick = {
                                showJoinInbox = true
                                scope.launch {
                                    inboxBusy = true
                                    teamsRepository.listPendingJoinRequests()
                                        .onSuccess { inboxRequests = it }
                                        .onFailure { inboxRequests = emptyList() }
                                    inboxBusy = false
                                }
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

            if (loading && profile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            } else {
                error?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (profile?.playerTeamId.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.team_no_team_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.team_no_team_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        createName = ""
                                        createTag = ""
                                        createError = null
                                        showCreate = true
                                    },
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Text(stringResource(R.string.profile_player_team_create))
                                }
                                OutlinedButton(
                                    onClick = {
                                        joinSearch = ""
                                        joinResults = emptyList()
                                        joinFeedback = null
                                        showJoin = true
                                    },
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Text(stringResource(R.string.profile_player_team_join))
                                }
                            }
                        }
                    }
                } else if (teamDetail != null) {
                    val team = teamDetail!!
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${team.displayName} [${team.tag}]",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 520.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                item {
                                    TeamTableHeader()
                                }
                                items(team.members, key = { it.userId }) { member ->
                                    TeamMemberTableRow(
                                        member = member,
                                        isLeader = isLeader,
                                        currentUserId = currentUserId,
                                        teamId = team.id,
                                        busy = membersBusy,
                                        onBusyChange = { membersBusy = it },
                                        onReload = { reloadProfileAndTeam() },
                                        teamsRepository = teamsRepository,
                                    )
                                }
                                if (isLeader) {
                                    item {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant,
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
                                                enabled = !membersBusy,
                                                label = { Text(stringResource(R.string.team_add_member_hint)) },
                                            )
                                            Button(
                                                enabled = !membersBusy && addNameDraft.trim().length >= 3,
                                                onClick = {
                                                    val username = addNameDraft.trim()
                                                    if (username.length < 3) return@Button
                                                    scope.launch {
                                                        membersBusy = true
                                                        teamsRepository.addMember(team.id, username)
                                                            .onSuccess {
                                                                addNameDraft = ""
                                                                reloadProfileAndTeam()
                                                            }
                                                            .onFailure { e ->
                                                                error = e.toUserMessageRu(res)
                                                            }
                                                        membersBusy = false
                                                    }
                                                },
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

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!createBusy) showCreate = false },
            title = { Text(stringResource(R.string.profile_player_team_create_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = {
                            createName = it.take(48)
                            createError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_field_team_full_name)) },
                        singleLine = true,
                        enabled = !createBusy,
                    )
                    OutlinedTextField(
                        value = createTag,
                        onValueChange = { v ->
                            createTag = v.filter { it.isLetter() }.take(3)
                            createError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_field_team_tag)) },
                        singleLine = true,
                        enabled = !createBusy,
                    )
                    createError?.let { e ->
                        Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !createBusy && createName.trim().length >= 2 && isValidThreeLetterTeamTag(createTag),
                    onClick = {
                        val n = createName.trim()
                        val tg = createTag.trim()
                        if (n.length < 2 || !isValidThreeLetterTeamTag(tg)) return@Button
                        scope.launch {
                            createBusy = true
                            createError = null
                            teamsRepository.createTeam(n, tg)
                                .onSuccess {
                                    showCreate = false
                                    reloadProfileAndTeam()
                                }
                                .onFailure {
                                    createError = context.getString(R.string.profile_player_team_save_error)
                                }
                            createBusy = false
                        }
                    },
                ) {
                    if (createBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.profile_action_save))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!createBusy) showCreate = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    if (showJoin) {
        AlertDialog(
            onDismissRequest = { if (!joinActionBusy) showJoin = false },
            title = { Text(stringResource(R.string.profile_player_team_join_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = joinSearch,
                        onValueChange = {
                            joinSearch = it
                            joinFeedback = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_player_team_join_hint)) },
                        singleLine = true,
                        enabled = !joinActionBusy,
                    )
                    if (joinSearchBusy) {
                        CircularProgressIndicator(
                            Modifier
                                .padding(8.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    joinFeedback?.let { f ->
                        Text(f, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(joinResults, key = { it.id }) { t ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "[${t.tag}] ${t.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    TextButton(
                                        enabled = !joinActionBusy,
                                        onClick = {
                                            scope.launch {
                                                joinActionBusy = true
                                                teamsRepository.submitJoinRequest(t.id)
                                                    .onSuccess {
                                                        joinFeedback = context.getString(
                                                            R.string.profile_player_team_join_sent,
                                                        )
                                                    }
                                                    .onFailure {
                                                        joinFeedback = context.getString(
                                                            R.string.profile_player_team_save_error,
                                                        )
                                                    }
                                                joinActionBusy = false
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.profile_player_team_join_submit))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (!joinActionBusy) showJoin = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    if (showJoinInbox) {
        AlertDialog(
            onDismissRequest = { if (!inboxBusy) showJoinInbox = false },
            title = { Text(stringResource(R.string.profile_join_inbox_title)) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    if (inboxBusy && inboxRequests.isEmpty()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
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
                                                inboxBusy = true
                                                teamsRepository.acceptJoinRequest(request.id)
                                                    .onSuccess {
                                                        teamsRepository.listPendingJoinRequests()
                                                            .onSuccess { inboxRequests = it }
                                                        reloadProfileAndTeam()
                                                    }
                                                inboxBusy = false
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.profile_join_accept))
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                inboxBusy = true
                                                teamsRepository.rejectJoinRequest(request.id)
                                                    .onSuccess {
                                                        teamsRepository.listPendingJoinRequests()
                                                            .onSuccess { inboxRequests = it }
                                                    }
                                                inboxBusy = false
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
                TextButton(onClick = { showJoinInbox = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TeamTableHeader() {
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
            modifier = Modifier.weight(1.3f),
        )
        Text(
            text = stringResource(R.string.team_col_role),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.8f),
        )
        Text(
            text = stringResource(R.string.team_col_telegram),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    val avatar = telegramAvatarUrl(member.telegramUsername)
    val telegram = telegramDisplayHandle(member.telegramUsername)
    val roleLabel = if (member.isLeader) {
        stringResource(R.string.team_role_leader)
    } else {
        member.role
    }
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
                    .weight(1.3f)
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
                    .weight(0.8f)
                    .padding(horizontal = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = telegram ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isLeader && !member.isLeader && member.userId != currentUserId) {
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
                                .onFailure { /* keep current state */ }
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
