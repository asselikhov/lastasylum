package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.screens.TeamLeaderDialogsHost
import com.lastasylum.alliance.ui.screens.TeamLeaderToolbar
import com.lastasylum.alliance.ui.screens.rememberTeamLeaderOverlayState
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.isOverlayIngameNow
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OverlayTeamOnlinePanel(
    teamsRepository: TeamsRepository,
    usersRepository: UsersRepository,
    onHudRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    val leaderUi = rememberTeamLeaderOverlayState()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<MyProfileDto?>(null) }
    var team by remember { mutableStateOf<TeamDetailDto?>(null) }
    var onlineMembers by remember { mutableStateOf<List<PlayerTeamMemberDto>>(emptyList()) }

    fun reload(forceTeamRefresh: Boolean = false) {
        scope.launch {
            loading = true
            error = null
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = OverlayTeamContextCache.load(
                        usersRepository = usersRepository,
                        teamsRepository = teamsRepository,
                        forceRefresh = forceTeamRefresh,
                    ).getOrThrow()
                    val p = usersRepository.getMyProfile().getOrThrow()
                    val t = teamsRepository.getTeam(ctx.teamId).getOrThrow()
                    val online = OverlayGameStatusHudRefresh.filterTeamIngameOverlayMembers(t.members)
                    Triple(p, t, online)
                }
            }
            loaded.onSuccess { (p, t, online) ->
                profile = p
                team = t
                onlineMembers = online
            }.onFailure { e ->
                error = e.toUserMessageRu(res)
                profile = null
                team = null
                onlineMembers = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    val t = team
    val p = profile
    val isLeader = p?.isPlayerTeamLeader == true
    val pending = p?.pendingPlayerTeamJoinRequests ?: 0

    TeamLeaderDialogsHost(
        teamId = t?.id,
        teamsRepository = teamsRepository,
        overlayUi = true,
        showAddMember = leaderUi.showAddMember,
        onShowAddMemberChange = { leaderUi.showAddMember = it },
        showEditTeam = leaderUi.showEditTeam,
        onShowEditTeamChange = { leaderUi.showEditTeam = it },
        showJoinInbox = leaderUi.showJoinInbox,
        onShowJoinInboxChange = { leaderUi.showJoinInbox = it },
        editTeamNameDraft = leaderUi.editTeamNameDraft,
        onEditTeamNameDraftChange = { leaderUi.editTeamNameDraft = it },
        editTeamTagDraft = leaderUi.editTeamTagDraft,
        onEditTeamTagDraftChange = { leaderUi.editTeamTagDraft = it },
        addNameDraft = leaderUi.addNameDraft,
        onAddNameDraftChange = { leaderUi.addNameDraft = it },
        membersBusy = leaderUi.membersBusy,
        onMembersBusyChange = { leaderUi.membersBusy = it },
        editNameBusy = leaderUi.editNameBusy,
        onEditNameBusyChange = { leaderUi.editNameBusy = it },
        inboxBusy = leaderUi.inboxBusy,
        onInboxBusyChange = { leaderUi.inboxBusy = it },
        inboxRequests = leaderUi.inboxRequests,
        onInboxRequestsChange = { leaderUi.inboxRequests = it },
        inboxFeedback = leaderUi.inboxFeedback,
        onInboxFeedbackChange = { leaderUi.inboxFeedback = it },
        onReloadTeam = {
            OverlayTeamContextCache.invalidate()
            reload(forceTeamRefresh = true)
        },
        onHudRefresh = onHudRefresh,
        onError = { msg -> error = msg },
    )

    Column(modifier.fillMaxSize()) {
        if (loading && t == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else if (error != null && t == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(SquadRelayDimens.contentPaddingHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else if (t != null) {
            TeamLeaderToolbar(
                team = t,
                subtitle = stringResource(R.string.overlay_team_online_count, onlineMembers.size),
                isLeader = isLeader,
                pendingJoinRequests = pending,
                membersBusy = leaderUi.membersBusy,
                editNameBusy = leaderUi.editNameBusy,
                overlayUi = true,
                onAddMember = {
                    leaderUi.addNameDraft = ""
                    leaderUi.showAddMember = true
                },
                onEditTeam = {
                    leaderUi.editTeamNameDraft = t.displayName.trim()
                    leaderUi.editTeamTagDraft = t.tag.trim()
                    leaderUi.showEditTeam = true
                },
                onOpenInbox = {
                    leaderUi.inboxFeedback = null
                    leaderUi.showJoinInbox = true
                    scope.launch {
                        leaderUi.inboxBusy = true
                        teamsRepository.listPendingJoinRequests()
                            .onSuccess { leaderUi.inboxRequests = it }
                            .onFailure { e ->
                                leaderUi.inboxRequests = emptyList()
                                error = e.toUserMessageRu(res)
                            }
                        leaderUi.inboxBusy = false
                    }
                },
            )
            when {
                onlineMembers.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.overlay_online_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = SquadRelayDimens.contentPaddingHorizontal,
                            end = SquadRelayDimens.contentPaddingHorizontal,
                            top = 4.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(onlineMembers, key = { it.userId }) { member ->
                            OverlayTeamOnlineMemberRow(member)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayTeamOnlineMemberRow(member: PlayerTeamMemberDto) {
    val avatarUrl = telegramAvatarUrl(member.telegramUsername)
    val letter = member.username.trim().take(1).uppercase().ifBlank { "?" }
    val inGame = isOverlayIngameNow(member.presenceStatus, member.lastPresenceAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = member.username,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (inGame) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFF81C784)),
            )
        }
    }
}
