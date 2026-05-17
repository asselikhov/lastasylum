package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamJoinRequestDto
import com.lastasylum.alliance.data.teams.TeamSearchResultDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.screens.teamforum.TeamForumNavHost
import com.lastasylum.alliance.ui.screens.teamnews.TeamNewsNavHost
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.ui.components.GlassSurface
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.lerp
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

enum class TeamMainSection {
    News,
    Forum,
    Members,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    currentUserId: String,
    teamsRepository: TeamsRepository,
    /** When set (e.g. overlay), open this section on first composition. */
    initialMainSection: TeamMainSection? = null,
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
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showEditTeamNameDialog by remember { mutableStateOf(false) }
    var editTeamNameDraft by remember { mutableStateOf("") }
    var editTeamTagDraft by remember { mutableStateOf("") }
    var editNameBusy by remember { mutableStateOf(false) }
    var roleEditMember by remember { mutableStateOf<PlayerTeamMemberDto?>(null) }
    var mainSectionOrdinal by rememberSaveable {
        mutableIntStateOf(initialMainSection?.ordinal ?: TeamMainSection.News.ordinal)
    }
    var forumTabReselectSignal by remember { mutableStateOf(0) }

    LaunchedEffect(mainSectionOrdinal) {
        if (mainSectionOrdinal !in TeamMainSection.entries.indices) {
            mainSectionOrdinal = TeamMainSection.News.ordinal
        }
    }

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
        delay(350)
        ensureActive()
        val q = joinSearch.trim()
        if (q.length < 1) {
            joinResults = emptyList()
            return@LaunchedEffect
        }
        joinSearchBusy = true
        teamsRepository.searchTeams(q)
            .onSuccess { joinResults = it }
            .onFailure { e ->
                joinResults = emptyList()
                joinFeedback = e.toUserMessageRu(res)
            }
        joinSearchBusy = false
    }

    val isLeader = profile?.isPlayerTeamLeader == true
    val pending = profile?.pendingPlayerTeamJoinRequests ?: 0
    val overlayUi = LocalOverlayUiMode.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Full-width content like Chat: small side padding is applied per-section/list.
            .padding(top = SquadRelayDimens.screenTopPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {

            if (loading && profile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    error?.let { err ->
                        val scheme = MaterialTheme.colorScheme
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = scheme.errorContainer.copy(alpha = 0.55f),
                            tonalElevation = 0.dp,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }

                    if (profile?.playerTeamId.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                            contentAlignment = Alignment.Center,
                        ) {
                            TeamNoTeamOnboardingCard(
                                onCreateTeam = {
                                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                    createName = ""
                                    createTag = ""
                                    createError = null
                                    showCreate = true
                                },
                                onJoinTeam = {
                                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                    joinSearch = ""
                                    joinResults = emptyList()
                                    joinFeedback = null
                                    showJoin = true
                                },
                            )
                        }
                    } else if (teamDetail != null) {
                        val team = teamDetail!!
                        val myTeamRole = remember(team.id, currentUserId, team.members) {
                            team.members.find { it.userId == currentUserId }?.teamRole ?: "R1"
                        }
                        val canPublishNews = remember(myTeamRole) {
                            myTeamRole == "R4" || myTeamRole == "R5"
                        }
                        val canManageForumTopics = remember(myTeamRole) {
                            myTeamRole == "R4" || myTeamRole == "R5"
                        }
                        val canModerateForumMessages = remember(myTeamRole) { myTeamRole == "R5" }
                        val enabledStickerPackKeys = remember(profile?.enabledStickerPacks) {
                            profile?.enabledStickerPacks?.toSet() ?: emptySet()
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f, fill = true)
                                .fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = Color.Transparent,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = SquadRelayDimens.panelInnerPadding,
                                            vertical = 10.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = "[${team.tag.uppercase()}]",
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
                                            text = stringResource(
                                                R.string.team_roster_count,
                                                team.members.size,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (isLeader) {
                                        IconButton(
                                            onClick = {
                                                OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                                addNameDraft = ""
                                                showAddMemberDialog = true
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
                                                editTeamNameDraft = team.displayName.trim()
                                                editTeamTagDraft = team.tag.trim()
                                                showEditTeamNameDialog = true
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
                                                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                                    showJoinInbox = true
                                                    scope.launch {
                                                        inboxBusy = true
                                                        teamsRepository.listPendingJoinRequests()
                                                            .onSuccess { inboxRequests = it }
                                                            .onFailure { e ->
                                                                inboxRequests = emptyList()
                                                                error = e.toUserMessageRu(res)
                                                            }
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
                                TeamSectionPills(
                                    selectedSection = TeamMainSection.entries[mainSectionOrdinal],
                                    onSelect = { section ->
                                        if (section == TeamMainSection.Forum &&
                                            TeamMainSection.entries[mainSectionOrdinal] == TeamMainSection.Forum
                                        ) {
                                            forumTabReselectSignal++
                                        } else {
                                            mainSectionOrdinal = section.ordinal
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = SquadRelayDimens.panelInnerPadding,
                                            vertical = 6.dp,
                                        ),
                                )
                                when (TeamMainSection.entries[mainSectionOrdinal]) {
                                    TeamMainSection.News -> {
                                        TeamNewsNavHost(
                                            teamId = team.id,
                                            currentUserId = currentUserId,
                                            myTeamRole = myTeamRole,
                                            canPublishNews = canPublishNews,
                                            teamsRepository = teamsRepository,
                                            modifier = Modifier
                                                .weight(1f, fill = true)
                                                .fillMaxWidth(),
                                        )
                                    }
                                    TeamMainSection.Forum -> {
                                        TeamForumNavHost(
                                            teamId = team.id,
                                            currentUserId = currentUserId,
                                            canManageTopics = canManageForumTopics,
                                            canModerateForumMessages = canModerateForumMessages,
                                            teamsRepository = teamsRepository,
                                            forumSocket = app.teamForumSocket,
                                            tokenStore = app.tokenStore,
                                            forumTabReselectSignal = forumTabReselectSignal,
                                            enabledStickerPackKeys = enabledStickerPackKeys,
                                            modifier = Modifier
                                                .weight(1f, fill = true)
                                                .fillMaxWidth(),
                                        )
                                    }
                                    TeamMainSection.Members -> {
                                        SquadTeamRoster(
                                            members = team.members,
                                            isSquadLeader = isLeader,
                                            currentUserId = currentUserId,
                                            teamId = team.id,
                                            busy = membersBusy,
                                            onBusyChange = { membersBusy = it },
                                            onReload = { reloadProfileAndTeam() },
                                            onError = { msg -> error = msg },
                                            teamsRepository = teamsRepository,
                                            onRequestEditMemberRole = { m ->
                                                OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                                roleEditMember = m
                                            },
                                            modifier = Modifier
                                                .weight(1f, fill = true)
                                                .fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }

    if (showCreate) {
        OverlayModalScope {
        AlertDialog(
            onDismissRequest = { if (!createBusy) showCreate = false },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            createTag = v.filter { it.isLetter() }.take(4)
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
                    enabled = !createBusy && createName.trim().length >= 2 && isValidTeamTag3to4Letters(createTag),
                    onClick = {
                        val n = createName.trim()
                        val tg = createTag.trim()
                        if (n.length < 2 || !isValidTeamTag3to4Letters(tg)) return@Button
                        scope.launch {
                            createBusy = true
                            createError = null
                            teamsRepository.createTeam(n, tg)
                                .onSuccess {
                                    showCreate = false
                                    reloadProfileAndTeam()
                                }
                                .onFailure { e ->
                                    createError = e.toUserMessageRu(res)
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
    }

    if (showJoin) {
        OverlayModalScope {
        AlertDialog(
            onDismissRequest = { if (!joinActionBusy) showJoin = false },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }

    if (showJoinInbox) {
        OverlayModalScope {
        AlertDialog(
            onDismissRequest = { if (!inboxBusy) showJoinInbox = false },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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

    val teamIdForDialogs = teamDetail?.id
    if (showAddMemberDialog && teamIdForDialogs != null) {
        OverlayModalScope {
        AlertDialog(
            onDismissRequest = { if (!membersBusy) showAddMemberDialog = false },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.team_add_member_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addNameDraft,
                        onValueChange = { addNameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !membersBusy,
                        label = { Text(stringResource(R.string.team_add_member_hint)) },
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !membersBusy && addNameDraft.trim().length >= 3,
                    onClick = {
                        val username = addNameDraft.trim()
                        if (username.length < 3) return@Button
                        scope.launch {
                            membersBusy = true
                            teamsRepository.addMember(teamIdForDialogs, username)
                                .onSuccess {
                                    addNameDraft = ""
                                    showAddMemberDialog = false
                                    reloadProfileAndTeam()
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            membersBusy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.team_add_member_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!membersBusy) showAddMemberDialog = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
        }
    }

    if (showEditTeamNameDialog && teamIdForDialogs != null) {
        OverlayModalScope {
        AlertDialog(
            onDismissRequest = { if (!editNameBusy) showEditTeamNameDialog = false },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            editTeamTagDraft = v.filter { it.isLetter() }.take(4)
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
                            editNameBusy = true
                            teamsRepository.updateTeamBranding(teamIdForDialogs, n, tg)
                                .onSuccess {
                                    showEditTeamNameDialog = false
                                    reloadProfileAndTeam()
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
    }

    roleEditMember?.let { member ->
        if (teamIdForDialogs != null) {
            OverlayModalScope {
            SquadMemberRoleEditDialog(
                member = member,
                onDismiss = { roleEditMember = null },
                onSaved = { reloadProfileAndTeam() },
                teamId = teamIdForDialogs,
                teamsRepository = teamsRepository,
                onError = { msg -> error = msg },
            )
            }
        }
    }
}

@Composable
private fun TeamNoTeamOnboardingCard(
    onCreateTeam: () -> Unit,
    onJoinTeam: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(28.dp)
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp),
        shape = cardShape,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = 0.38f),
                                scheme.primary.copy(alpha = 0.06f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = scheme.primary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.team_no_team_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.team_no_team_body),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onCreateTeam,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_player_team_create),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.team_no_team_create_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onJoinTeam,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = scheme.onSurface,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = scheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_player_team_join),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.team_no_team_join_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamSectionPills(
    selectedSection: TeamMainSection,
    onSelect: (TeamMainSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = TeamMainSection.entries.map { section ->
        val titleRes = when (section) {
            TeamMainSection.News -> R.string.team_tab_news
            TeamMainSection.Forum -> R.string.team_tab_forum
            TeamMainSection.Members -> R.string.team_tab_members
        }
        com.lastasylum.alliance.ui.components.SquadSegmentTab(
            id = section.name,
            label = stringResource(titleRes),
        )
    }
    com.lastasylum.alliance.ui.components.SquadSegmentTabBar(
        tabs = tabs,
        selectedId = selectedSection.name,
        onSelect = { id ->
            TeamMainSection.entries.firstOrNull { it.name == id }?.let(onSelect)
        },
        modifier = modifier,
    )
}
