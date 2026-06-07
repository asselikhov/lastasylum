package com.lastasylum.alliance.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamPresenceSocketManager
import com.lastasylum.alliance.data.teams.TeamPresenceSocketState
import com.lastasylum.alliance.ui.util.OVERLAY_ONLINE_PANEL_POLL_MS
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.data.voice.TeamVoicePresenceStore
import com.lastasylum.alliance.data.voice.VoicePresenceRosterSync
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.screens.TeamLeaderDialogsHost
import com.lastasylum.alliance.ui.screens.rememberTeamLeaderOverlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OverlayTeamOnlinePanel(
    teamsRepository: TeamsRepository,
    usersRepository: UsersRepository,
    teamPresenceSocket: TeamPresenceSocketManager,
    tokenProvider: () -> String?,
    openJoinInboxInitially: Boolean = false,
    onOpenJoinInboxConsumed: () -> Unit = {},
    initialJoinRequestCount: Int = 0,
    onHudRefresh: () -> Unit,
    onClose: () -> Unit = {},
    onIngameCountChanged: (Int) -> Unit = {},
    onJoinRequestCountChanged: (Int) -> Unit = {},
    onSendReactionToUser: (userId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    val leaderUi = rememberTeamLeaderOverlayState()
    val appContainer = remember(context) { AppContainer.from(context) }
    val voiceSession = appContainer.overlayVoiceSession
    val voiceRosterSync = remember(appContainer) { VoicePresenceRosterSync(appContainer) }

    val controller = remember(teamsRepository, usersRepository, teamPresenceSocket, appContainer) {
        OverlayTeamOnlineController(
            scope = scope,
            teamsRepository = teamsRepository,
            usersRepository = usersRepository,
            teamPresenceSocket = teamPresenceSocket,
            launchDiskCache = appContainer.launchDiskCache,
            tokenProvider = tokenProvider,
            baseUrl = BuildConfig.API_BASE_URL,
            resources = res,
            onIngameCountChanged = onIngameCountChanged,
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val presenceSocketState by teamPresenceSocket.connectionState.collectAsStateWithLifecycle(lifecycleOwner)

    LaunchedEffect(controller) {
        withContext(Dispatchers.IO) {
            usersRepository.updatePresence("ingame")
        }
        controller.start()
    }
    LaunchedEffect(presenceSocketState) {
        if (presenceSocketState == TeamPresenceSocketState.Connected) {
            controller.onPresenceSocketConnected()
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.refresh(force = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(voiceRosterSync) {
        voiceRosterSync.acquire()
        onDispose { voiceRosterSync.release() }
    }
    val uiState by controller.state.collectAsStateWithLifecycle(lifecycleOwner)
    var longPressMember by remember { mutableStateOf<OverlayOnlineMemberUiModel?>(null) }
    val voicePeers by TeamVoicePresenceStore.peers.collectAsStateWithLifecycle(lifecycleOwner)
    val hasLocalVoiceSession = voiceSession != null

    LaunchedEffect(openJoinInboxInitially) {
        if (!openJoinInboxInitially) return@LaunchedEffect
        leaderUi.inboxFeedback = null
        leaderUi.showJoinInbox = true
        scope.launch {
            leaderUi.inboxBusy = true
            teamsRepository.loadPendingJoinRequestsForOverlay(context)
                .onSuccess { leaderUi.inboxRequests = it }
                .onFailure { err ->
                    leaderUi.inboxRequests = emptyList()
                    leaderUi.inboxFeedback = joinInboxLoadErrorMessage(context, err)
                }
            leaderUi.inboxBusy = false
        }
        onOpenJoinInboxConsumed()
    }

    val selfId = uiState.profile?.id
    val memberIds = remember(uiState.baseSections) {
        uiState.baseSections.flatMap { it.items }.map { it.userId }
    }
    val needsVoiceFilter = uiState.activeFilterChip == OverlayOnlineFilterChip.WithMic
    val voiceFlagsByUserId = remember(
        voicePeers,
        voiceSession?.micOn,
        voiceSession?.soundOn,
        selfId,
        memberIds,
        needsVoiceFilter,
    ) {
        if (!needsVoiceFilter) {
            emptyMap()
        } else {
            buildVoiceFlagsMap(
                memberUserIds = memberIds,
                voicePeers = voicePeers,
                selfUserId = selfId,
                localMicOn = voiceSession?.micOn,
                localSoundOn = voiceSession?.soundOn,
            )
        }
    }
    val displaySections = remember(
        uiState.baseSections,
        uiState.searchQuery,
        uiState.activeFilterChip,
        voiceFlagsByUserId,
    ) {
        applyOnlinePanelFilters(
            baseSections = uiState.baseSections,
            query = uiState.searchQuery,
            chip = uiState.activeFilterChip,
            voiceFlagsByUserId = voiceFlagsByUserId,
        )
    }

    val team = uiState.team
    val profile = uiState.profile
    val isLeader = profile?.isPlayerTeamLeader == true
    var joinRequestCount by remember(initialJoinRequestCount) {
        mutableStateOf(initialJoinRequestCount)
    }
    LaunchedEffect(isLeader, initialJoinRequestCount, profile?.pendingPlayerTeamJoinRequests) {
        if (!isLeader) {
            joinRequestCount = 0
            return@LaunchedEffect
        }
        joinRequestCount = initialJoinRequestCount.coerceAtLeast(
            profile.pendingPlayerTeamJoinRequests,
        )
        val refreshed = withContext(Dispatchers.IO) {
            OverlayGameStatusHudRefresh.loadTeamJoinRequestCount(context)
        }
        joinRequestCount = refreshed
        onJoinRequestCountChanged(refreshed)
    }
    val selfLabel = stringResource(R.string.overlay_online_self)

    val openJoinInbox: () -> Unit = {
        leaderUi.inboxFeedback = null
        leaderUi.showJoinInbox = true
        scope.launch {
            leaderUi.inboxBusy = true
            teamsRepository.loadPendingJoinRequestsForOverlay(context)
                .onSuccess {
                    leaderUi.inboxRequests = it
                    joinRequestCount = it.size
                    onJoinRequestCountChanged(it.size)
                }
                .onFailure { err ->
                    leaderUi.inboxRequests = emptyList()
                    leaderUi.inboxFeedback = joinInboxLoadErrorMessage(context, err)
                }
            leaderUi.inboxBusy = false
        }
    }

    TeamLeaderDialogsHost(
        teamId = team?.id,
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
            controller.refresh(force = true)
            onHudRefresh()
        },
        onHudRefresh = onHudRefresh,
        onError = { _ -> /* surfaced via controller on next refresh */ },
    )

    longPressMember?.let { member ->
        OverlayAwareBottomSheet(onDismissRequest = { longPressMember = null }) {
            Text(
                text = member.username,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            TextButton(
                onClick = {
                    longPressMember = null
                    onSendReactionToUser(member.userId)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.overlay_online_action_reaction))
            }
            TextButton(
                onClick = {
                    copyUsernameToClipboard(context, member.username)
                    longPressMember = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.overlay_online_action_copy_nick))
            }
        }
    }

    OverlayOnlinePanelScaffold(
        modifier = modifier,
        displaySections = displaySections,
        searchQuery = uiState.searchQuery,
        voiceSelfUserId = selfId,
        voiceLocalMicOn = voiceSession?.micOn,
        voiceLocalSoundOn = voiceSession?.soundOn,
        voicePeers = voicePeers,
        hasLocalVoiceSession = hasLocalVoiceSession,
        activeFilterChip = uiState.activeFilterChip,
        loading = uiState.loading,
        refreshing = uiState.refreshing,
        error = uiState.error,
        staleDataHint = uiState.staleDataHint,
        selfLabel = selfLabel,
        onSearchQuery = controller::onSearchQuery,
        onFilterChip = controller::onFilterChip,
        onRefresh = { controller.refresh(force = true) },
        onMemberLongClick = { longPressMember = it },
        topBar = {
            val realtimeLabel = when (presenceSocketState) {
                TeamPresenceSocketState.Connected ->
                    stringResource(R.string.overlay_online_realtime_live)
                else ->
                    stringResource(
                        R.string.overlay_online_realtime_poll,
                        OVERLAY_ONLINE_PANEL_POLL_MS / 1_000,
                    )
            }
            OverlayHudPanelHeader(
                title = stringResource(R.string.overlay_online_title),
                subtitle = stringResource(
                    R.string.overlay_online_summary_ingame,
                    uiState.ingameCount,
                ) + " · $realtimeLabel",
                onClose = onClose,
                subtitleTrailing = if (team != null && isLeader) {
                    {
                        OverlayOnlineLeaderToolbarActions(
                            pendingJoinRequests = joinRequestCount,
                            membersBusy = leaderUi.membersBusy,
                            editNameBusy = leaderUi.editNameBusy,
                            onAddMember = {
                                leaderUi.addNameDraft = ""
                                leaderUi.showAddMember = true
                            },
                            onEditTeam = {
                                leaderUi.editTeamNameDraft = team.displayName.trim()
                                leaderUi.editTeamTagDraft = team.tag.trim()
                                leaderUi.showEditTeam = true
                            },
                            onOpenInbox = openJoinInbox,
                        )
                    }
                } else {
                    null
                },
            )
        },
    )
}

@Composable
private fun RowScope.OverlayOnlineLeaderToolbarActions(
    pendingJoinRequests: Int,
    membersBusy: Boolean,
    editNameBusy: Boolean,
    onAddMember: () -> Unit,
    onEditTeam: () -> Unit,
    onOpenInbox: () -> Unit,
) {
    val tokens = OverlayOnlineMemberTokens
    IconButton(
        onClick = onAddMember,
        enabled = !membersBusy,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PersonAdd,
            contentDescription = stringResource(R.string.team_cd_add_member),
            tint = tokens.borderLive,
            modifier = Modifier.size(20.dp),
        )
    }
    IconButton(
        onClick = onEditTeam,
        enabled = !membersBusy && !editNameBusy,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = stringResource(R.string.team_cd_edit_team_name),
            tint = tokens.borderLive,
            modifier = Modifier.size(20.dp),
        )
    }
    val inboxButton: @Composable () -> Unit = {
        IconButton(
            onClick = onOpenInbox,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = stringResource(R.string.profile_join_inbox_cd),
                tint = tokens.borderLive,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    if (pendingJoinRequests > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(OverlayBadgeFormat.label(pendingJoinRequests))
                }
            },
        ) {
            inboxButton()
        }
    } else {
        inboxButton()
    }
}

private fun copyUsernameToClipboard(context: Context, username: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("username", username))
    Toast.makeText(context, username, Toast.LENGTH_SHORT).show()
}
