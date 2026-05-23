package com.lastasylum.alliance.overlay

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamPresenceSocketManager
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.data.voice.TeamVoicePresenceStore
import com.lastasylum.alliance.data.voice.VoicePeerState
import com.lastasylum.alliance.ui.components.team.TeamMemberPresenceCard
import com.lastasylum.alliance.ui.screens.TeamLeaderDialogsHost
import com.lastasylum.alliance.ui.screens.TeamLeaderToolbar
import com.lastasylum.alliance.ui.screens.rememberTeamLeaderOverlayState
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.OVERLAY_ONLINE_PANEL_POLL_MS
import com.lastasylum.alliance.ui.util.formatOverlayPresenceAgeRu
import com.lastasylum.alliance.ui.util.isOverlayIngameNow
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PresenceListItem(
    val member: PlayerTeamMemberDto,
    val inGameNow: Boolean,
    val key: String,
)

@Composable
fun OverlayTeamOnlinePanel(
    teamsRepository: TeamsRepository,
    usersRepository: UsersRepository,
    teamPresenceSocket: TeamPresenceSocketManager,
    tokenProvider: () -> String?,
    openJoinInboxInitially: Boolean = false,
    onOpenJoinInboxConsumed: () -> Unit = {},
    onHudRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    val leaderUi = rememberTeamLeaderOverlayState()
    val voicePeers by TeamVoicePresenceStore.peers.collectAsState()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<MyProfileDto?>(null) }
    var team by remember { mutableStateOf<TeamDetailDto?>(null) }
    var teamId by remember { mutableStateOf<String?>(null) }
    var onlineMembers by remember { mutableStateOf<List<PlayerTeamMemberDto>>(emptyList()) }
    var recentlyActive by remember { mutableStateOf<List<PlayerTeamMemberDto>>(emptyList()) }

    fun applyBootstrap(forceTeamRefresh: Boolean, showBlockingSpinner: Boolean) {
        scope.launch {
            if (showBlockingSpinner || team == null) {
                loading = true
            }
            error = null
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = OverlayTeamContextCache.load(
                        usersRepository = usersRepository,
                        teamsRepository = teamsRepository,
                        forceRefresh = forceTeamRefresh,
                    ).getOrThrow()
                    if (forceTeamRefresh) {
                        OverlayTeamPresenceCache.invalidate()
                    }
                    val t = OverlayTeamContextCache.loadTeamDetail(
                        teamId = ctx.teamId,
                        teamsRepository = teamsRepository,
                        forceRefresh = forceTeamRefresh,
                    ).getOrThrow()
                    val presence = OverlayTeamPresenceCache.load(
                        teamId = ctx.teamId,
                        teamsRepository = teamsRepository,
                        forceRefresh = forceTeamRefresh,
                    ).getOrThrow()
                    val p = usersRepository.getMyProfile().getOrThrow()
                    PresenceLoadResult(
                        profile = p,
                        team = t,
                        teamId = ctx.teamId,
                        ingame = presence.ingame,
                        recentlyActive = presence.recentlyActive,
                    )
                }
            }
            loaded.onSuccess { result ->
                profile = result.profile
                team = result.team
                teamId = result.teamId
                onlineMembers = result.ingame
                recentlyActive = result.recentlyActive
            }.onFailure { e ->
                error = e.toUserMessageRu(res)
                if (team == null) {
                    profile = null
                    teamId = null
                    onlineMembers = emptyList()
                    recentlyActive = emptyList()
                }
            }
            loading = false
        }
    }

    fun refreshPresenceOnly() {
        val tid = teamId?.trim().orEmpty()
        if (tid.isEmpty()) return
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                OverlayTeamPresenceCache.load(
                    teamId = tid,
                    teamsRepository = teamsRepository,
                    forceRefresh = false,
                )
            }
            loaded.onSuccess { presence ->
                onlineMembers = presence.ingame
                recentlyActive = presence.recentlyActive
                error = null
            }
        }
    }

    val presenceSocketListener: (com.lastasylum.alliance.data.teams.TeamPresenceSocketEvent) -> Unit =
        remember {
            { event ->
                if (!isOverlayIngameNow(event.presenceStatus, event.lastPresenceAt)) {
                    onlineMembers = onlineMembers.filter { it.userId != event.userId }
                } else {
                    refreshPresenceOnly()
                }
            }
        }

    LaunchedEffect(teamId) {
        val tid = teamId ?: return@LaunchedEffect
        teamPresenceSocket.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            teamId = tid,
            tokenProvider = tokenProvider,
        )
    }

    DisposableEffect(teamId, presenceSocketListener) {
        teamPresenceSocket.addPresenceListener(presenceSocketListener)
        onDispose {
            teamPresenceSocket.removePresenceListener(presenceSocketListener)
        }
    }

    LaunchedEffect(Unit) {
        applyBootstrap(forceTeamRefresh = false, showBlockingSpinner = team == null)
        while (isActive) {
            delay(OVERLAY_ONLINE_PANEL_POLL_MS)
            refreshPresenceOnly()
        }
    }

    LaunchedEffect(openJoinInboxInitially) {
        if (!openJoinInboxInitially) return@LaunchedEffect
        leaderUi.inboxFeedback = null
        leaderUi.showJoinInbox = true
        scope.launch {
            leaderUi.inboxBusy = true
            teamsRepository.listPendingJoinRequests()
                .onSuccess { leaderUi.inboxRequests = it }
            leaderUi.inboxBusy = false
        }
        onOpenJoinInboxConsumed()
    }

    val t = team
    val p = profile
    val isLeader = p?.isPlayerTeamLeader == true
    val pending = p?.pendingPlayerTeamJoinRequests ?: 0
    val selfLabel = stringResource(R.string.overlay_online_self)
    val ingameItems = remember(onlineMembers, p?.id) {
        onlineMembers
            .filter { isOverlayIngameNow(it.presenceStatus, it.lastPresenceAt) }
            .map { member ->
                PresenceListItem(
                    member = member,
                    inGameNow = true,
                    key = "ingame:${member.userId}",
                )
            }
    }
    val recentItems = remember(recentlyActive, p?.id) {
        recentlyActive
            .filter { !isOverlayIngameNow(it.presenceStatus, it.lastPresenceAt) }
            .map { member ->
            PresenceListItem(
                member = member,
                inGameNow = false,
                key = "recent:${member.userId}",
            )
        }
    }
    val totalShown = onlineMembers.size + recentlyActive.size

    TeamLeaderDialogsHost(
        teamId = t?.id,
        teamsRepository = teamsRepository,
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
            OverlayTeamPresenceCache.invalidate()
            applyBootstrap(forceTeamRefresh = true, showBlockingSpinner = false)
            onHudRefresh()
        },
        onHudRefresh = onHudRefresh,
        onError = { msg -> error = msg },
    )

    Column(modifier.fillMaxSize()) {
        when {
            loading && t == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            error != null && t == null -> {
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
            }
            t != null -> {
                TeamLeaderToolbar(
                    team = t,
                    activeServerNumber = profile?.activeServerNumber,
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
                    onRefresh = {
                        OverlayTeamPresenceCache.invalidate()
                        refreshPresenceOnly()
                        onHudRefresh()
                    },
                )
                if (totalShown == 0) {
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
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = SquadRelayDimens.contentPaddingHorizontal,
                            end = SquadRelayDimens.contentPaddingHorizontal,
                            top = 8.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (ingameItems.isNotEmpty()) {
                            item(key = "hdr_ingame") {
                                Text(
                                    text = stringResource(R.string.overlay_online_section_ingame),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                            items(ingameItems, key = { it.key }) { item ->
                                PresenceMemberRow(
                                    item = item,
                                    selfUserId = p?.id,
                                    selfLabel = selfLabel,
                                    voicePeers = voicePeers,
                                )
                            }
                        }
                        if (recentItems.isNotEmpty()) {
                            item(key = "hdr_recent") {
                                Text(
                                    text = stringResource(R.string.overlay_online_section_recent),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                )
                            }
                            items(recentItems, key = { it.key }) { item ->
                                PresenceMemberRow(
                                    item = item,
                                    selfUserId = p?.id,
                                    selfLabel = selfLabel,
                                    voicePeers = voicePeers,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresenceMemberRow(
    item: PresenceListItem,
    selfUserId: String?,
    selfLabel: String,
    voicePeers: Map<String, VoicePeerState>,
) {
    val member = item.member
    val isSelf = member.userId == selfUserId
    val peer = voicePeers[member.userId]
    val squadRole = member.teamRole.trim().uppercase().ifBlank { "R1" }
    val presenceAge = formatOverlayPresenceAgeRu(member.lastPresenceAt)
    val inGameNow = item.inGameNow ||
        isOverlayIngameNow(member.presenceStatus, member.lastPresenceAt)
    val displayName = if (isSelf) {
        "${member.username} ($selfLabel)"
    } else {
        member.username
    }
    TeamMemberPresenceCard(
        username = member.username,
        telegramUsername = member.telegramUsername,
        squadRole = squadRole,
        displayName = displayName,
        presenceSubtitle = presenceAge,
        inGameNow = inGameNow,
        showIngameAvatarRing = inGameNow,
        micOn = peer?.micOn == true,
        soundOn = peer?.soundOn == true,
        showVoiceBadges = inGameNow,
    )
}

private data class PresenceLoadResult(
    val profile: MyProfileDto,
    val team: TeamDetailDto,
    val teamId: String,
    val ingame: List<PlayerTeamMemberDto>,
    val recentlyActive: List<PlayerTeamMemberDto>,
)
