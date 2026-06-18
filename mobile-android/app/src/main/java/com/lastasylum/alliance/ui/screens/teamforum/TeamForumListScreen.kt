package com.lastasylum.alliance.ui.screens.teamforum

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.ReadCursorSession
import com.lastasylum.alliance.data.teams.TeamInboxUnread.displayedForumTopicUnread
import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.data.teams.TeamForumMarkRead
import com.lastasylum.alliance.data.teams.TeamForumReadCursorSync
import com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent
import com.lastasylum.alliance.data.teams.forum.ForumRepository
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayAwareAlertDialog
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.overlay.OverlayReactionLogJumpToUnreadFab
import com.lastasylum.alliance.ui.components.CenteredScreenLoading
import com.lastasylum.alliance.ui.components.PremiumEmptyState
import com.lastasylum.alliance.ui.components.premium.PremiumGradientIconFab
import com.lastasylum.alliance.ui.components.team.FeedAnimationTier
import com.lastasylum.alliance.ui.components.team.ForumTopicCardTokens
import com.lastasylum.alliance.ui.components.team.ForumTopicFeedCard
import com.lastasylum.alliance.ui.components.team.ForumTopicGhostIconButton
import com.lastasylum.alliance.ui.components.team.ForumTopicListHeader
import com.lastasylum.alliance.ui.components.team.buildVisibleUnreadRankMap
import com.lastasylum.alliance.ui.components.team.filterForumTopics
import com.lastasylum.alliance.ui.components.team.forumTopicAnimationTier
import com.lastasylum.alliance.ui.teamforum.ForumListViewModel
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.formatForumTopicListTimeRu
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FORUM_MARK_READ_LIST_KEY = "list"

@Composable
fun TeamForumListScreen(
    teamId: String,
    currentUserId: String,
    canManageTopics: Boolean,
    topicTitles: MutableMap<String, String>,
    topicSnapshots: MutableMap<String, TeamForumTopicDto>,
    refreshNonce: Int,
    sectionActive: Boolean = true,
    topicActivityPatch: TeamForumTopicActivityEvent? = null,
    topicPinPatch: TeamForumTopicPinChangedEvent? = null,
    onInboxChanged: () -> Unit = {},
    onForumTopicsSynced: (List<TeamForumTopicDto>, Map<String, Int>) -> Unit = { _, _ -> },
    onOpenTopic: (TeamForumTopicDto) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    onProvideMarkReadAction: (String, (() -> Unit)?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val res = context.resources
    val overlayUi = LocalOverlayUiMode.current
    val scope = rememberCoroutineScope()
    val listViewModel: ForumListViewModel = viewModel(key = "forum_list_$teamId") {
        ForumListViewModel.create(context.applicationContext as Application, teamId)
    }
    val listUiState by listViewModel.state.collectAsStateWithLifecycle()
    val topics = listUiState.topics
    val loading = listUiState.loading
    val searchQuery = listUiState.searchQuery
    var actionError by remember { mutableStateOf<String?>(null) }
    val displayError = actionError ?: if (topics.isEmpty()) listUiState.error else null

    val app = remember { AppContainer.from(context.applicationContext) }
    val forumRepository = remember { app.forumRepository }
    val forumPrefs = remember { app.teamForumPreferences }
    val lastReadByTopic = remember { mutableStateMapOf<String, String>() }
    var readCursorRevision by remember(teamId) { mutableIntStateOf(0) }
    remember(teamId, currentUserId) {
        val uid = currentUserId.trim()
        if (uid.isNotBlank()) {
            ReadCursorSession.bind(
                app.chatRoomPreferences,
                forumPrefs,
                app.userSettingsPreferences,
                uid,
            )
        }
        forumPrefs.loadAllLastReadMessageIds(teamId).forEach { (topicId, messageId) ->
            if (messageId.isBlank()) return@forEach
            val current = lastReadByTopic[topicId]
            if (current == null || isObjectIdNewer(messageId, current)) {
                lastReadByTopic[topicId] = messageId
            }
        }
        readCursorRevision++
    }
    var menuTopic by remember { mutableStateOf<TeamForumTopicDto?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }
    var createBusy by remember { mutableStateOf(false) }
    var editTopic by remember { mutableStateOf<TeamForumTopicDto?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }
    var deleteTopic by remember { mutableStateOf<TeamForumTopicDto?>(null) }

    fun mergeTopicReadCursor(topicId: String, messageId: String) {
        if (messageId.isBlank()) return
        val current = lastReadByTopic[topicId]
        if (current == null || isObjectIdNewer(messageId, current)) {
            lastReadByTopic[topicId] = messageId
            forumPrefs.setLastReadMessageId(teamId, topicId, messageId)
        }
    }

    fun hydrateReadCursorsFromTopics(rows: List<TeamForumTopicDto>) {
        // Device read cursors come from [forumPrefs] in [reload] only.
        // Do not copy [TeamForumTopicDto.lastReadMessageId] into prefs — that suppresses
        // per-topic unread badges and fire animation while server unreadCount is still > 0.
    }

    fun effectiveTopicUnread(topic: TeamForumTopicDto): Int =
        displayedForumTopicUnread(
            topic = topic,
            localLastReadMessageId = lastReadByTopic[topic.id],
            optimisticFloor = listUiState.optimisticUnreadFloorByTopic[topic.id] ?: 0,
        )

    fun onTopicsUpdated(rows: List<TeamForumTopicDto>) {
        hydrateReadCursorsFromTopics(rows)
        rows.forEach { t ->
            topicTitles[t.id] = t.title
            topicSnapshots[t.id] = t
        }
        onForumTopicsSynced(rows, listUiState.optimisticUnreadFloorByTopic)
        val staleReads = rows.mapNotNull { topic ->
            if (topic.unreadCount > 0 && effectiveTopicUnread(topic) == 0) {
                lastReadByTopic[topic.id]?.let { topic.id to it }
            } else {
                null
            }
        }
        if (staleReads.isNotEmpty()) {
            scope.launch {
                staleReads.forEach { (topicId, localLast) ->
                    forumRepository.markForumTopicRead(teamId, topicId, localLast)
                }
            }
        }
    }

    var reloadJob by remember(teamId) { mutableStateOf<Job?>(null) }
    var inboxSyncedForSection by remember(teamId) { mutableStateOf(false) }

    fun refreshReadCursorsFromPrefs() {
        forumPrefs.loadAllLastReadMessageIds(teamId).forEach { (topicId, messageId) ->
            mergeTopicReadCursor(topicId, messageId)
        }
        readCursorRevision++
    }

    fun reload(force: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            withContext(Dispatchers.IO) {
                refreshReadCursorsFromPrefs()
            }
            actionError = null
            if (currentUserId.isNotBlank()) {
                listViewModel.reload(force = force)
            } else {
                forumRepository.syncTopics("", teamId, bypassCache = force)
                    .onSuccess { onTopicsUpdated(it) }
                    .onFailure { e ->
                        if (topics.isEmpty()) {
                            actionError = e.toUserMessageRu(res)
                        }
                    }
            }
        }
    }

    LaunchedEffect(topics) {
        onTopicsUpdated(topics)
    }

    LaunchedEffect(teamId) {
        onProvideMarkReadAction(FORUM_MARK_READ_LIST_KEY) {
            scope.launch {
                TeamForumMarkRead.markAllTopicsRead(forumRepository, currentUserId, forumPrefs, teamId)
                refreshReadCursorsFromPrefs()
                onInboxChanged()
                reload()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { onProvideMarkReadAction(FORUM_MARK_READ_LIST_KEY, null) }
    }

    LaunchedEffect(teamId, sectionActive, currentUserId, overlayUi) {
        if (!sectionActive) return@LaunchedEffect
        val uid = currentUserId.trim()
        if (uid.isEmpty() || inboxSyncedForSection) return@LaunchedEffect
        ReadCursorSession.bind(
            app.chatRoomPreferences,
            forumPrefs,
            app.userSettingsPreferences,
            uid,
        )
        if (overlayUi) {
            refreshReadCursorsFromPrefs()
            withContext(Dispatchers.IO) {
                TeamForumReadCursorSync.repairOnly(
                    teamsRepository = app.teamsRepository,
                    forumPrefs = forumPrefs,
                    teamId = teamId,
                )
                refreshReadCursorsFromPrefs()
            }
        } else {
            withContext(Dispatchers.IO) {
                ReadCursorSession.syncAllInboxReadCursors(
                    usersRepository = app.usersRepository,
                    teamsRepository = app.teamsRepository,
                    chatRepository = app.chatRepository,
                    chatRoomPreferences = app.chatRoomPreferences,
                    teamForumPreferences = forumPrefs,
                    userSettingsPreferences = app.userSettingsPreferences,
                )
                refreshReadCursorsFromPrefs()
            }
        }
        inboxSyncedForSection = true
        reload(force = true)
    }

    LaunchedEffect(teamId, refreshNonce, sectionActive, inboxSyncedForSection) {
        if (!sectionActive) return@LaunchedEffect
        if (currentUserId.isNotBlank() && !inboxSyncedForSection) return@LaunchedEffect
        reload()
    }

    LaunchedEffect(topicActivityPatch) {
        val event = topicActivityPatch ?: return@LaunchedEffect
        listViewModel.applyTopicActivity(event)
    }

    LaunchedEffect(topicPinPatch) {
        val event = topicPinPatch ?: return@LaunchedEffect
        listViewModel.applyTopicPin(event)
    }

    val topicListState = rememberLazyListState(
        initialFirstVisibleItemIndex = 0,
        initialFirstVisibleItemScrollOffset = 0,
    )
    val filteredTopics by remember(topics, searchQuery) {
        derivedStateOf {
            filterForumTopics(
                topics = topics,
                query = searchQuery,
            )
        }
    }
    val visibleIndices by remember(topicListState) {
        derivedStateOf {
            topicListState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
        }
    }
    val visibleUnreadRanks by remember(
        topicListState,
        filteredTopics,
        readCursorRevision,
        listUiState.optimisticUnreadFloorByTopic,
    ) {
        derivedStateOf {
            buildVisibleUnreadRankMap(
                topicListState.layoutInfo.visibleItemsInfo.map { it.index }.sorted(),
            ) { idx ->
                idx in filteredTopics.indices && effectiveTopicUnread(filteredTopics[idx]) > 0
            }
        }
    }
    val visibleReadRanks by remember(topicListState, filteredTopics, readCursorRevision) {
        derivedStateOf {
            buildVisibleUnreadRankMap(
                topicListState.layoutInfo.visibleItemsInfo.map { it.index }.sorted(),
            ) { idx ->
                idx in filteredTopics.indices && effectiveTopicUnread(filteredTopics[idx]) == 0
            }
        }
    }
    val forumTopicUnreadTotal = remember(topics, readCursorRevision, listUiState.optimisticUnreadFloorByTopic) {
        topics.sumOf { effectiveTopicUnread(it).coerceAtLeast(0) }
    }
    val firstUnreadTopicIndex = remember(
        filteredTopics,
        readCursorRevision,
        listUiState.optimisticUnreadFloorByTopic,
    ) {
        filteredTopics.indexOfLast { effectiveTopicUnread(it) > 0 }
    }
    val isFirstUnreadTopicVisible by remember(topicListState, firstUnreadTopicIndex) {
        derivedStateOf {
            if (firstUnreadTopicIndex < 0) return@derivedStateOf true
            topicListState.layoutInfo.visibleItemsInfo.any { it.index == firstUnreadTopicIndex }
        }
    }
    val showJumpToUnreadTopics by remember(
        overlayUi,
        forumTopicUnreadTotal,
        firstUnreadTopicIndex,
        isFirstUnreadTopicVisible,
    ) {
        derivedStateOf {
            overlayUi &&
                forumTopicUnreadTotal > 0 &&
                firstUnreadTopicIndex >= 0 &&
                !isFirstUnreadTopicVisible
        }
    }

    Column(Modifier.fillMaxSize()) {
        displayError?.let { err ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SquadRelayDimens.itemGap, vertical = SquadRelayDimens.itemGap),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
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
        Box(Modifier.fillMaxSize()) {
            when {
                loading && topics.isEmpty() -> {
                    CenteredScreenLoading()
                }
                topics.isEmpty() -> {
                    PremiumEmptyState(
                        icon = Icons.Outlined.Forum,
                        title = stringResource(R.string.team_forum_empty),
                        body = "",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                else -> {
                    val listTopPad = if (overlayUi) 0.dp else 4.dp
                    Column(Modifier.fillMaxSize()) {
                        if (!overlayUi) {
                            ForumTopicListHeader(
                                searchQuery = searchQuery,
                                onSearchQueryChange = listViewModel::onSearchQueryChange,
                            )
                        }
                        if (filteredTopics.isEmpty()) {
                            PremiumEmptyState(
                                icon = Icons.Outlined.Forum,
                                title = stringResource(R.string.team_forum_empty),
                                body = stringResource(R.string.team_forum_search_hint),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            )
                        } else {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                LazyColumn(
                                    state = topicListState,
                                    contentPadding = PaddingValues(
                                        start = SquadRelayDimens.contentPaddingHorizontal,
                                        end = SquadRelayDimens.contentPaddingHorizontal,
                                        top = listTopPad,
                                        bottom = 88.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(ForumTopicCardTokens.listSpacing),
                                ) {
                                    itemsIndexed(
                                        filteredTopics,
                                        key = { _, t -> t.id },
                                        contentType = { _, _ -> "forum_topic" },
                                    ) { index, t ->
                                        val unreadRaw = effectiveTopicUnread(t)
                                        val unread = unreadRaw
                                        val unreadRank = visibleUnreadRanks[index] ?: -1
                                        val readRank = visibleReadRanks[index] ?: -1
                                        val animationTier = if (!inboxSyncedForSection) {
                                            FeedAnimationTier.Off
                                        } else if (unread == 0) {
                                            FeedAnimationTier.Off
                                        } else {
                                            forumTopicAnimationTier(
                                                unread = unread > 0,
                                                isVisible = index in visibleIndices,
                                                visibleUnreadRank = unreadRank,
                                                visibleReadRank = if (unread > 0) -1 else readRank,
                                                listSize = filteredTopics.size,
                                                sectionActive = sectionActive,
                                                overlayMode = overlayUi,
                                            ).let { tier ->
                                                if (unread > 0 && index in visibleIndices && tier == FeedAnimationTier.Off) {
                                                    FeedAnimationTier.Lite
                                                } else {
                                                    tier
                                                }
                                            }
                                        }
                                        val timeIso = t.lastMessageAt ?: t.createdAt
                                        ForumTopicFeedCard(
                                            topic = t,
                                            listIndex = index,
                                            messageMeta = formatForumTopicListTimeRu(timeIso),
                                            displayUnreadCount = unread,
                                            displayMessageCount = t.messageCount.coerceAtLeast(0),
                                            animationTier = animationTier,
                                            emberBoost = if (unread > 0 && unreadRank in 0..2) 1.45f else 1f,
                                            onClick = { onOpenTopic(t) },
                                            menu = {
                                                if (canManageTopics) {
                                                    Box {
                                                        ForumTopicGhostIconButton(
                                                            onClick = { menuTopic = t },
                                                            contentDescription = stringResource(
                                                                R.string.team_forum_topic_menu_cd,
                                                            ),
                                                        )
                                                        DropdownMenu(
                                                            expanded = menuTopic?.id == t.id,
                                                            onDismissRequest = { menuTopic = null },
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.team_forum_edit_topic)) },
                                                                onClick = {
                                                                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                                                    menuTopic = null
                                                                    editTopic = t
                                                                    editTitle = t.title
                                                                },
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.team_forum_delete_topic)) },
                                                                onClick = {
                                                                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                                                    menuTopic = null
                                                                    deleteTopic = t
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                if (overlayUi) {
                                    OverlayReactionLogJumpToUnreadFab(
                                        visible = showJumpToUnreadTopics,
                                        unreadCount = forumTopicUnreadTotal,
                                        onClick = {
                                            if (firstUnreadTopicIndex >= 0) {
                                                scope.launch {
                                                    topicListState.animateScrollToItem(
                                                        firstUnreadTopicIndex,
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 8.dp)
                                            .zIndex(6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (canManageTopics && !overlayUi) {
                PremiumGradientIconFab(
                    onClick = {
                        OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                        createTitle = ""
                        showCreate = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.team_forum_new_topic_cd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    if (showCreate) {
        OverlayModalScope(preparedByCaller = true) {
            OverlayAwareAlertDialog(
                onDismissRequest = { if (!createBusy) showCreate = false },
                title = { Text(stringResource(R.string.team_forum_new_topic_title)) },
                text = {
                    OutlinedTextField(
                        value = createTitle,
                        onValueChange = { createTitle = it.take(200) },
                        label = { Text(stringResource(R.string.team_forum_topic_title_label)) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !createBusy,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !createBusy && createTitle.trim().isNotEmpty(),
                        onClick = {
                            scope.launch {
                                createBusy = true
                                forumRepository.createForumTopic(teamId, createTitle)
                                    .onSuccess {
                                        topicTitles[it.id] = it.title
                                        topicSnapshots[it.id] = it
                                        showCreate = false
                                        reload()
                                    }
                                    .onFailure { e -> actionError = e.toUserMessageRu(res) }
                                createBusy = false
                            }
                        },
                    ) {
                        Text(stringResource(R.string.profile_action_save))
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

    editTopic?.let { topic ->
        OverlayModalScope(preparedByCaller = true) {
            OverlayAwareAlertDialog(
                onDismissRequest = { if (!editBusy) editTopic = null },
                title = { Text(stringResource(R.string.team_forum_edit_topic_title)) },
                text = {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it.take(200) },
                        label = { Text(stringResource(R.string.team_forum_topic_title_label)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !editBusy,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !editBusy && editTitle.trim().isNotEmpty(),
                        onClick = {
                            scope.launch {
                                editBusy = true
                                forumRepository.updateForumTopic(teamId, topic.id, editTitle)
                                    .onSuccess {
                                        topicTitles[it.id] = it.title
                                        topicSnapshots[it.id] = it
                                        editTopic = null
                                        reload()
                                    }
                                    .onFailure { e -> actionError = e.toUserMessageRu(res) }
                                editBusy = false
                            }
                        },
                    ) {
                        Text(stringResource(R.string.profile_action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!editBusy) editTopic = null }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }
    }

    deleteTopic?.let { topic ->
        OverlayModalScope(preparedByCaller = true) {
            OverlayAwareAlertDialog(
                onDismissRequest = { deleteTopic = null },
                title = { Text(stringResource(R.string.team_forum_delete_topic_title)) },
                text = { Text(stringResource(R.string.team_forum_delete_topic_body, topic.title)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                forumRepository.deleteForumTopic(teamId, topic.id)
                                    .onSuccess {
                                        deleteTopic = null
                                        reload()
                                    }
                                    .onFailure { e -> actionError = e.toUserMessageRu(res) }
                            }
                        },
                    ) {
                        Text(
                            stringResource(R.string.team_forum_delete_confirm),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTopic = null }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }
    }
}
