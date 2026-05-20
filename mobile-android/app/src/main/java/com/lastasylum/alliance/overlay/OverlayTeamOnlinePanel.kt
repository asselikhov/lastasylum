package com.lastasylum.alliance.overlay

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.lastasylum.alliance.data.voice.TeamVoicePresenceStore
import com.lastasylum.alliance.ui.components.OverlayMemberVoiceBadges
import com.lastasylum.alliance.ui.screens.TeamLeaderDialogsHost
import com.lastasylum.alliance.ui.screens.TeamLeaderToolbar
import com.lastasylum.alliance.ui.screens.rememberTeamLeaderOverlayState
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class OnlineListItem(
    val member: PlayerTeamMemberDto,
    val key: String,
)

/** Squad rank R1–R5 for sorting team roster in overlay. */
private fun squadRank(role: String): Int = when (role.trim().uppercase()) {
    "R5" -> 5
    "R4" -> 4
    "R3" -> 3
    "R2" -> 2
    "R1" -> 1
    else -> 0
}

private fun buildOnlineListItems(members: List<PlayerTeamMemberDto>): List<OnlineListItem> =
    members
        .sortedWith(
            compareByDescending<PlayerTeamMemberDto> { squadRank(it.teamRole) }
                .thenBy { it.username.lowercase() },
        )
        .map { member ->
            OnlineListItem(member = member, key = member.userId)
        }

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
    val voicePeers by TeamVoicePresenceStore.peers.collectAsState()

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
    val listItems = remember(onlineMembers) { buildOnlineListItems(onlineMembers) }

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
                            top = 8.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(listItems, key = { it.key }) { item ->
                            val peer = voicePeers[item.member.userId]
                            OverlayTeamOnlineMemberCard(
                                member = item.member,
                                micOn = peer?.micOn == true,
                                soundOn = peer?.soundOn == true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayTeamOnlineMemberCard(
    member: PlayerTeamMemberDto,
    micOn: Boolean,
    soundOn: Boolean,
) {
    val avatarUrl = telegramAvatarUrl(member.telegramUsername)
    val letter = member.username.trim().take(1).uppercase().ifBlank { "?" }
    val squadRole = member.teamRole.trim().uppercase().ifBlank { "R1" }
    val roleCd = stringResource(R.string.overlay_member_squad_rank_cd, squadRole)
    val scheme = MaterialTheme.colorScheme
    val ingameRing = Color(0xFF81C784)

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
                    .background(ingameRing.copy(alpha = 0.35f))
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(scheme.primaryContainer),
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = member.username,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = scheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.semantics { contentDescription = roleCd },
                ) {
                    Text(
                        text = squadRole,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            OverlayMemberVoiceBadges(
                micOn = micOn,
                soundOn = soundOn,
            )
        }
    }
}
