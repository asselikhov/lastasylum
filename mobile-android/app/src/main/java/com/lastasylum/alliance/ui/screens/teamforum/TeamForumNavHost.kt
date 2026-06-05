package com.lastasylum.alliance.ui.screens.teamforum

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lastasylum.alliance.ui.chat.AttachmentPreviewOverlay
import com.lastasylum.alliance.ui.chat.ChatComposer
import com.lastasylum.alliance.ui.chat.ChatComposerBar
import com.lastasylum.alliance.ui.chat.stabilizeComposerImageUris
import com.lastasylum.alliance.ui.chat.capForumMessagesOldestFirst
import com.lastasylum.alliance.ui.chat.mergePreservingForumMedia
import com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthCap
import com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthFraction
import com.lastasylum.alliance.ui.chat.ChatScrollToLatestFab
import com.lastasylum.alliance.ui.chat.LocalChatBubbleMaxWidth
import com.lastasylum.alliance.ui.chat.LocalMessageExpandScrollCompensation
import com.lastasylum.alliance.ui.chat.scrollReverseChatCompensateExpand
import com.lastasylum.alliance.ui.chat.LocalOpenRemoteChatImagePreview
import com.lastasylum.alliance.ui.chat.isAtReverseChatBottom
import com.lastasylum.alliance.ui.chat.scrollReverseChatRevealLatest
import com.lastasylum.alliance.ui.chat.scrollTimelineItemToViewportCenter
import com.lastasylum.alliance.ui.chat.LocalChatHighlightMessageId
import com.lastasylum.alliance.ui.chat.MessengerImagesPreviewHost
import com.lastasylum.alliance.ui.chat.toDisplayChatMessage
import com.lastasylum.alliance.ui.chat.queryDisplayName
import com.lastasylum.alliance.ui.theme.roleAccentColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.widget.Toast
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.ReadCursorSession
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.teams.TeamForumMessageDeletedEvent
import com.lastasylum.alliance.data.teams.TeamForumMessageReactionEvent
import com.lastasylum.alliance.data.teams.TeamForumSocketManager
import com.lastasylum.alliance.data.chat.ChatReaction
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent
import com.lastasylum.alliance.ui.chat.ForumPinCoordinator
import com.lastasylum.alliance.ui.chat.PinnedMessageBar
import com.lastasylum.alliance.ui.chat.TopicPinSnapshot
import com.lastasylum.alliance.ui.chat.formatPinnedMetaLine
import com.lastasylum.alliance.ui.chat.forumPinPreviewDisplayState
import com.lastasylum.alliance.ui.chat.isForumPinnedPreviewLikelyDeleted
import com.lastasylum.alliance.ui.chat.isForumPinnedPreviewUnavailable
import com.lastasylum.alliance.ui.chat.PinnedMessagesSheet
import com.lastasylum.alliance.ui.chat.forumLazyIndexForMessageId
import com.lastasylum.alliance.ui.chat.jumpToForumPinnedMessage
import com.lastasylum.alliance.ui.chat.resolvedThumbnailUrl
import com.lastasylum.alliance.ui.chat.toPinnedPreview
import com.lastasylum.alliance.ui.util.formatForumTopicTimeRu
import com.lastasylum.alliance.data.teams.TeamForumTypingEvent
import com.lastasylum.alliance.data.teams.TeamForumMarkRead
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.ui.chat.MessageActionOpenRequest
import com.lastasylum.alliance.ui.chat.MessageContextMenuActions
import com.lastasylum.alliance.ui.chat.MessageContextMenuPopup
import com.lastasylum.alliance.ui.chat.MessageContextMenuScrim
import com.lastasylum.alliance.ui.chat.saveChatImagesToGallery
import com.lastasylum.alliance.ui.chat.resolvedChatAttachmentImageUrl
import com.lastasylum.alliance.ui.util.copyForumMessageToClipboard
import com.lastasylum.alliance.ui.util.forumMessageHasMenuCopyAction
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayReactionLogJumpToUnreadFab
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayAwareAlertDialog
import com.lastasylum.alliance.overlay.OverlayInteractionSuppressEffect
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lastasylum.alliance.ui.chat.ChatStickerFormat
import androidx.compose.ui.geometry.Rect
import com.lastasylum.alliance.ui.chat.chatDayKey
import com.lastasylum.alliance.ui.chat.formatChatDaySeparator
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.chat.ForumTimelineEntry
import com.lastasylum.alliance.ui.components.PremiumEmptyState
import com.lastasylum.alliance.ui.components.premium.PremiumGlassBar
import com.lastasylum.alliance.ui.components.premium.PremiumGradientIconFab
import com.lastasylum.alliance.ui.components.CenteredScreenLoading
import com.lastasylum.alliance.ui.components.team.ForumTopicCardTokens
import com.lastasylum.alliance.ui.components.team.ForumTopicFeedCard
import com.lastasylum.alliance.ui.components.team.ForumTopicGhostIconButton
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import java.io.File
import java.io.InputStream
import java.util.UUID
import android.content.ContentResolver
import android.os.ParcelFileDescriptor
import com.lastasylum.alliance.ui.util.formatForumTopicTimeRu
import java.util.Locale

private object ForumRoutes {
    const val LIST = "forum_list"
    fun topic(id: String) = "forum_topic/$id"
}

private const val FORUM_MARK_READ_LIST_KEY = "list"

private fun forumMarkReadTopicKey(topicId: String) = "topic/${topicId.trim()}"

@Composable
fun TeamForumNavHost(
    teamId: String,
    currentUserId: String,
    canManageTopics: Boolean,
    /** R4/R5 may edit/delete others' messages (matches backend). */
    canModerateForumMessages: Boolean,
    teamsRepository: TeamsRepository,
    forumSocket: TeamForumSocketManager,
    tokenStore: TokenStore,
    modifier: Modifier = Modifier,
    sectionActive: Boolean = true,
    forumTabReselectSignal: Int = 0,
    /** Wire keys of sticker packs the current user may send. */
    enabledStickerPackKeys: Set<String> = emptySet(),
    onForumTopicsSynced: (List<TeamForumTopicDto>) -> Unit = {},
    onForumInboxChanged: () -> Unit = {},
    onRegisterMarkReadAction: ((() -> Unit)?) -> Unit = {},
) {
    val nav = rememberNavController()
    val topicTitles = remember { mutableStateMapOf<String, String>() }
    val topicSnapshots = remember { mutableStateMapOf<String, TeamForumTopicDto>() }
    var listRefreshNonce by remember { mutableIntStateOf(0) }
    var topicActivityPatch by remember {
        mutableStateOf<com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent?>(null)
    }
    var topicPinPatch by remember { mutableStateOf<TeamForumTopicPinChangedEvent?>(null) }
    var markReadHandlers by remember { mutableStateOf<Map<String, () -> Unit>>(emptyMap()) }
    val registerMarkReadAction: (String, (() -> Unit)?) -> Unit = { key, action ->
        markReadHandlers = if (action == null) {
            markReadHandlers - key
        } else {
            markReadHandlers + (key to action)
        }
    }
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val activeMarkReadAction = remember(markReadHandlers, navBackStackEntry) {
        when (val route = navBackStackEntry?.destination?.route) {
            ForumRoutes.LIST -> markReadHandlers[FORUM_MARK_READ_LIST_KEY]
            else -> {
                if (route != null && route.startsWith("forum_topic/")) {
                    val topicId = navBackStackEntry?.arguments?.getString("topicId").orEmpty()
                    if (topicId.isNotBlank()) markReadHandlers[forumMarkReadTopicKey(topicId)] else null
                } else {
                    null
                }
            }
        }
    }
    LaunchedEffect(activeMarkReadAction) {
        onRegisterMarkReadAction(activeMarkReadAction)
    }
    LaunchedEffect(listRefreshNonce) {
        if (listRefreshNonce > 0) onForumInboxChanged()
    }
    DisposableEffect(teamId, sectionActive) {
        if (!sectionActive) {
            onDispose { }
        } else {
            val onTopicActivity: (com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent) -> Unit =
                { event ->
                    if (event.senderUserId.trim() != currentUserId.trim()) {
                        topicActivityPatch = event
                    }
                }
            val onTopicPin: (TeamForumTopicPinChangedEvent) -> Unit = { event ->
                topicPinPatch = event
            }
            forumSocket.addTopicActivityListener(onTopicActivity)
            forumSocket.addTopicPinChangedListener(onTopicPin)
            forumSocket.connectTeamInbox(
                com.lastasylum.alliance.BuildConfig.API_BASE_URL,
                teamId,
            ) { tokenStore.getAccessToken() }
            onDispose {
                forumSocket.removeTopicActivityListener(onTopicActivity)
                forumSocket.removeTopicPinChangedListener(onTopicPin)
            }
        }
    }
    LaunchedEffect(forumTabReselectSignal) {
        if (forumTabReselectSignal > 0) {
            nav.popBackStack(ForumRoutes.LIST, inclusive = false)
        }
    }
    LaunchedEffect(topicPinPatch) {
        val event = topicPinPatch ?: return@LaunchedEffect
        val existing = topicSnapshots[event.topicId]
        if (existing != null) {
            topicSnapshots[event.topicId] = existing.copy(
                pinnedMessageId = event.pinnedMessageId,
                pinnedAt = event.pinnedAt,
                pinnedByUserId = event.pinnedByUserId,
                pinnedMessage = event.pinnedMessage,
                pinnedMessages = event.pinnedMessages,
            )
        }
    }
    NavHost(
        navController = nav,
        startDestination = ForumRoutes.LIST,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(ForumRoutes.LIST) {
            TeamForumListRoute(
                teamId = teamId,
                currentUserId = currentUserId,
                canManageTopics = canManageTopics,
                teamsRepository = teamsRepository,
                topicTitles = topicTitles,
                topicSnapshots = topicSnapshots,
                refreshNonce = listRefreshNonce,
                sectionActive = sectionActive,
                topicActivityPatch = topicActivityPatch,
                topicPinPatch = topicPinPatch,
                onInboxChanged = onForumInboxChanged,
                onForumTopicsSynced = onForumTopicsSynced,
                onOpenTopic = { t ->
                    topicTitles[t.id] = t.title
                    topicSnapshots[t.id] = t
                    nav.navigate(ForumRoutes.topic(t.id))
                },
                onBack = { },
                onProvideMarkReadAction = registerMarkReadAction,
            )
        }
        composable(
            route = "forum_topic/{topicId}",
            arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
        ) { entry ->
            val topicId = entry.arguments?.getString("topicId")
            DisposableEffect(topicId) {
                onDispose { listRefreshNonce++ }
            }
            if (topicId.isNullOrBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { nav.popBackStack() }) {
                        Text(stringResource(R.string.team_news_cd_back))
                    }
                }
                return@composable
            }
            val title = topicTitles[topicId].orEmpty()
            val topicSnapshot = topicSnapshots[topicId]
            TeamForumTopicChatRoute(
                teamId = teamId,
                topicId = topicId,
                topicTitle = title,
                topicSnapshot = topicSnapshot,
                currentUserId = currentUserId,
                canModerateMessages = canModerateForumMessages,
                teamsRepository = teamsRepository,
                forumSocket = forumSocket,
                tokenStore = tokenStore,
                sectionActive = sectionActive,
                enabledStickerPackKeys = enabledStickerPackKeys,
                onBack = {
                    nav.popBackStack()
                    listRefreshNonce++
                },
                onProvideMarkReadAction = registerMarkReadAction,
                onTopicSnapshotUpdate = { topicSnapshots[topicId] = it },
            )
        }
    }
}

@Composable
private fun TeamForumListRoute(
    teamId: String,
    currentUserId: String,
    canManageTopics: Boolean,
    teamsRepository: TeamsRepository,
    topicTitles: MutableMap<String, String>,
    topicSnapshots: MutableMap<String, TeamForumTopicDto>,
    refreshNonce: Int,
    sectionActive: Boolean = true,
    topicActivityPatch: com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent? = null,
    topicPinPatch: TeamForumTopicPinChangedEvent? = null,
    onInboxChanged: () -> Unit = {},
    onForumTopicsSynced: (List<TeamForumTopicDto>) -> Unit = {},
    onOpenTopic: (TeamForumTopicDto) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    onProvideMarkReadAction: (String, (() -> Unit)?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val res = context.resources
    val overlayUi = LocalOverlayUiMode.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val topics = remember { mutableStateListOf<TeamForumTopicDto>() }
    val app = remember { AppContainer.from(context.applicationContext) }
    val forumPrefs = remember { app.teamForumPreferences }
    val lastReadByTopic = remember { mutableStateMapOf<String, String>() }
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
        rows.forEach { topic ->
            topic.lastReadMessageId?.trim()?.takeIf { it.isNotBlank() }?.let { mid ->
                mergeTopicReadCursor(topic.id, mid)
            }
        }
    }

    fun effectiveTopicUnread(topic: TeamForumTopicDto): Int =
        effectiveUnreadCount(
            serverUnread = topic.unreadCount,
            lastReadMessageId = topic.lastReadMessageId,
            localLastReadMessageId = lastReadByTopic[topic.id],
        )

    fun applyTopicRows(rows: List<TeamForumTopicDto>) {
        topics.clear()
        hydrateReadCursorsFromTopics(rows)
        val patchedRows = rows.map { topic ->
            if (effectiveTopicUnread(topic) == 0 && topic.unreadCount > 0) {
                topic.copy(unreadCount = 0)
            } else {
                topic
            }
        }
        topics.addAll(patchedRows)
        patchedRows.forEach { t ->
            topicTitles[t.id] = t.title
            topicSnapshots[t.id] = t
        }
        onForumTopicsSynced(patchedRows)
        patchedRows.filter { topic ->
            topic.unreadCount > 0 && effectiveTopicUnread(topic) == 0
        }.forEach { topic ->
            val localLast = lastReadByTopic[topic.id] ?: return@forEach
            scope.launch {
                teamsRepository.markForumTopicRead(teamId, topic.id, localLast)
            }
        }
    }

    fun reload() {
        scope.launch {
            val (diskTopics, lastReadSnapshot) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val disk = if (currentUserId.isNotBlank()) {
                    app.launchDiskCache.loadForumTopics(currentUserId, teamId)
                } else {
                    null
                }
                disk to forumPrefs.loadAllLastReadMessageIds(teamId)
            }
            lastReadSnapshot.forEach { (topicId, messageId) ->
                mergeTopicReadCursor(topicId, messageId)
            }
            if (!diskTopics.isNullOrEmpty()) {
                applyTopicRows(diskTopics)
                loading = false
            } else {
                loading = true
            }
            error = null
            teamsRepository.listForumTopics(teamId)
                .onSuccess {
                    if (currentUserId.isNotBlank()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            app.launchDiskCache.saveForumTopics(currentUserId, teamId, it)
                        }
                    }
                    applyTopicRows(it)
                }
                .onFailure { e ->
                    if (topics.isEmpty()) error = e.toUserMessageRu(res)
                }
            loading = false
        }
    }

    LaunchedEffect(teamId) {
        onProvideMarkReadAction(FORUM_MARK_READ_LIST_KEY) {
            scope.launch {
                TeamForumMarkRead.markAllTopicsRead(teamsRepository, forumPrefs, teamId)
                reload()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { onProvideMarkReadAction(FORUM_MARK_READ_LIST_KEY, null) }
    }

    LaunchedEffect(teamId, refreshNonce, currentUserId, sectionActive) {
        if (!sectionActive) return@LaunchedEffect
        val uid = currentUserId.trim()
        if (uid.isNotEmpty()) {
            ReadCursorSession.bind(
                app.chatRoomPreferences,
                forumPrefs,
                app.userSettingsPreferences,
                uid,
            )
        }
        reload()
    }

    LaunchedEffect(topicActivityPatch) {
        val event = topicActivityPatch ?: return@LaunchedEffect
        val idx = topics.indexOfFirst { it.id == event.topicId }
        if (idx >= 0) {
            val row = topics[idx]
            topics[idx] = row.copy(
                unreadCount = row.unreadCount + 1,
                messageCount = row.messageCount + 1,
                lastMessageAt = java.time.Instant.now().toString(),
            )
            onInboxChanged()
        }
    }

    LaunchedEffect(topicPinPatch) {
        val event = topicPinPatch ?: return@LaunchedEffect
        val idx = topics.indexOfFirst { it.id == event.topicId }
        if (idx >= 0) {
            val row = topics[idx]
            topics[idx] = row.copy(
                pinnedMessageId = event.pinnedMessageId,
                pinnedAt = event.pinnedAt,
                pinnedByUserId = event.pinnedByUserId,
                pinnedMessage = event.pinnedMessage,
                pinnedMessages = event.pinnedMessages,
            )
        }
    }

    val topicListState = rememberLazyListState(
        initialFirstVisibleItemIndex = 0,
        initialFirstVisibleItemScrollOffset = 0,
    )
    val forumTopicUnreadTotal = remember(topics) {
        topics.sumOf { effectiveTopicUnread(it).coerceAtLeast(0) }
    }
    val firstUnreadTopicIndex = remember(topics) {
        topics.indexOfLast { effectiveTopicUnread(it) > 0 }
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
        error?.let { err ->
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
                    val listTopPad = if (overlayUi) 0.dp else 8.dp
                    Box(Modifier.fillMaxSize()) {
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
                            topics,
                            key = { _, t -> t.id },
                            contentType = { _, _ -> "forum_topic" },
                        ) { index, t ->
                            ForumTopicFeedCard(
                                topic = t,
                                listIndex = index,
                                messageMeta = t.lastMessageAt?.let { formatForumTopicTimeRu(it) } ?: "—",
                                displayUnreadCount = effectiveTopicUnread(t),
                                animationsEnabled = sectionActive,
                                onClick = {
                                    onOpenTopic(t)
                                },
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
                                        topicListState.animateScrollToItem(firstUnreadTopicIndex)
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
                            teamsRepository.createForumTopic(teamId, createTitle)
                                .onSuccess {
                                    topicTitles[it.id] = it.title
                                    topicSnapshots[it.id] = it
                                    showCreate = false
                                    reload()
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
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
                            teamsRepository.updateForumTopic(teamId, topic.id, editTitle)
                                .onSuccess {
                                    topicTitles[it.id] = it.title
                                    topicSnapshots[it.id] = it
                                    editTopic = null
                                    reload()
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
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
                            teamsRepository.deleteForumTopic(teamId, topic.id)
                                .onSuccess {
                                    deleteTopic = null
                                    reload()
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                        }
                    },
                ) {
                    Text(stringResource(R.string.team_forum_delete_confirm), color = MaterialTheme.colorScheme.error)
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

private data class ForumScrollAnchor(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val timelineSize: Int,
)

private data class ForumLoadOlderSignal(
    val lastVisibleIndex: Int,
    val totalItems: Int,
)

@Composable
private fun ForumTopicOverlayBackChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.team_detail_back_cd)
    PremiumGlassBar(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun TeamForumTopicChatRoute(
    teamId: String,
    topicId: String,
    @Suppress("UNUSED_PARAMETER") topicTitle: String,
    topicSnapshot: TeamForumTopicDto?,
    currentUserId: String,
    canModerateMessages: Boolean,
    teamsRepository: TeamsRepository,
    forumSocket: TeamForumSocketManager,
    tokenStore: TokenStore,
    sectionActive: Boolean = true,
    onBack: () -> Unit,
    enabledStickerPackKeys: Set<String> = emptySet(),
    onProvideMarkReadAction: (String, (() -> Unit)?) -> Unit = { _, _ -> },
    onTopicSnapshotUpdate: (TeamForumTopicDto) -> Unit = {},
) {
    val context = LocalContext.current
    val res = context.resources
    val overlayUi = LocalOverlayUiMode.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val messages = remember { mutableStateListOf<TeamForumMessageDto>() }
    var loading by remember { mutableStateOf(true) }
    var hasMoreOlder by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var pickedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var attachmentPreviewStartIndex by remember { mutableStateOf<Int?>(null) }
    var pendingApkFileId by remember { mutableStateOf<String?>(null) }
    var pendingApkLabel by remember { mutableStateOf<String?>(null) }
    var uploadingImage by remember { mutableStateOf(false) }
    var uploadingFile by remember { mutableStateOf(false) }
    var downloadingForumFileUrl by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var typingHint by remember { mutableStateOf<String?>(null) }
    // messages is maintained oldest-first; avoid per-update sorting allocations (jank/GC).
    val stableMessages = messages
    var messagesGeneration by remember(teamId, topicId) { mutableIntStateOf(0) }
    fun bumpMessagesGeneration() {
        messagesGeneration++
    }
    val listDerived = rememberForumMessagesListDerived(stableMessages, messagesGeneration, listState)
    var newMessagesWhileScrolledUp by remember(teamId, topicId) { mutableIntStateOf(0) }
    var lastCountedNewestId by remember(teamId, topicId) { mutableStateOf<String?>(null) }
    val isNearBottom by remember(listState) {
        derivedStateOf { listState.isAtReverseChatBottom() }
    }
    val showScrollToLatestFab by remember(listState, listDerived, hasMoreOlder, stableMessages.size, loading) {
        derivedStateOf {
            !isNearBottom &&
                stableMessages.isNotEmpty() &&
                !loading
        }
    }

    fun trimForumMessagesInMemory() {
        capForumMessagesOldestFirst(messages)
        bumpMessagesGeneration()
    }

    var pendingForumScrollAnchor by remember(teamId, topicId) {
        mutableStateOf<ForumScrollAnchor?>(null)
    }
    val timelineSize = listDerived.timeline.size
    val app = remember { AppContainer.from(context.applicationContext) }
    val forumPrefs = remember { app.teamForumPreferences }
    var lastReadCursor by remember { mutableStateOf<String?>(null) }

    var editingForumMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var replyToMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var activeActionMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    val dismissMessageActions: () -> Unit = {
        activeActionMessageId = null
    }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var deletingSelection by remember { mutableStateOf(false) }
    var highlightMessageId by remember { mutableStateOf<String?>(null) }
    val pinHistoryPrefs = remember { AppContainer.from(context).pinHistoryPreferences }
    LaunchedEffect(currentUserId) {
        pinHistoryPrefs.bindUser(currentUserId)
    }
    val pinScopeKey = remember(teamId, topicId, currentUserId) {
        pinHistoryPrefs.forumScopeKey(teamId, topicId)
    }
    val pinCoordinator = remember(pinScopeKey) {
        ForumPinCoordinator(pinHistoryPrefs, pinScopeKey)
    }
    var pinRevision by remember(teamId, topicId) { mutableIntStateOf(0) }
    fun bumpPinUi() {
        pinRevision++
    }
    var pinNotice by remember { mutableStateOf<String?>(null) }
    var showForumPinnedSheet by remember { mutableStateOf(false) }
    var pendingForumPinMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var remoteImagePreview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImages = remember {
        { urls: List<String>, idx: Int -> remoteImagePreview = urls to idx }
    }

    BackHandler(enabled = selectedMessageIds.isNotEmpty() && !deletingSelection) {
        selectedMessageIds = emptySet()
        dismissMessageActions()
    }

    fun clearPendingAttachment() {
        pickedImageUris = emptyList()
        attachmentPreviewStartIndex = null
        pendingApkFileId = null
        pendingApkLabel = null
    }

    fun publishCoordinatorPinSnapshot() {
        val base = topicSnapshot ?: return
        onTopicSnapshotUpdate(
            base.copy(
                pinnedMessageId = pinCoordinator.pinnedMessageId,
                pinnedAt = pinCoordinator.pinnedAt,
                pinnedByUserId = pinCoordinator.pinnedByUserId,
                pinnedMessage = pinCoordinator.pinnedMessage,
                pinnedMessages = pinCoordinator.pinnedMessages,
            ),
        )
    }

    fun applyTopicPin(event: TeamForumTopicPinChangedEvent) {
        if (event.teamId != teamId || event.topicId != topicId) return
        pinCoordinator.applyTopicPin(event, stableMessages)
        publishCoordinatorPinSnapshot()
        bumpPinUi()
    }

    fun refreshTopicPinFromServer() {
        if (pinCoordinator.pinInFlight) return
        scope.launch {
            teamsRepository.listForumTopics(teamId, bypassCache = true, view = "full")
                .onSuccess { topics ->
                    val topic = topics.find { it.id == topicId } ?: return@onSuccess
                    pinCoordinator.applyTopicFromServer(topic, stableMessages)
                    onTopicSnapshotUpdate(topic)
                    bumpPinUi()
                }
        }
    }

    LaunchedEffect(teamId, topicId) {
        pinCoordinator.onEnterTopic(topicSnapshot)
        topicSnapshot?.let { snapshot ->
            pinCoordinator.applyTopicFromServer(snapshot, stableMessages)
        }
        refreshTopicPinFromServer()
        bumpPinUi()
    }

    LaunchedEffect(stableMessages.size, pinCoordinator.pinnedMessageId) {
        if (pinCoordinator.pinnedMessageId != null) {
            pinCoordinator.applyPinBarUi(stableMessages)
            bumpPinUi()
        }
    }

    fun pinForumMessage(messageId: String, previewSource: TeamForumMessageDto? = null) {
        val trimmedId = messageId.trim()
        if (trimmedId.isEmpty() || pinCoordinator.pinInFlight) return
        val snapshot = TopicPinSnapshot(
            pinnedMessageId = pinCoordinator.pinnedMessageId,
            pinnedAt = pinCoordinator.pinnedAt,
            pinnedByUserId = pinCoordinator.pinnedByUserId,
            pinnedMessage = pinCoordinator.pinnedMessage,
        )
        pinCoordinator.pinInFlight = true
        pinCoordinator.prepareOptimisticPin(trimmedId, previewSource, stableMessages, currentUserId)
        bumpPinUi()
        scope.launch {
            teamsRepository.pinForumTopicMessage(teamId, topicId, trimmedId)
                .onSuccess { topic ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.onPinSuccess(topic, stableMessages)
                    onTopicSnapshotUpdate(topic)
                    bumpPinUi()
                    pinNotice = res.getString(R.string.forum_pinned_toast_pinned)
                }
                .onFailure { e ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.rollbackTo(snapshot, stableMessages)
                    bumpPinUi()
                    pinNotice = e.toUserMessageRu(res)
                }
        }
    }

    fun unpinOneForumMessage(messageId: String) {
        if (pinCoordinator.pinInFlight) return
        val trimmedId = messageId.trim()
        if (trimmedId.isEmpty()) return
        val snapshot = TopicPinSnapshot(
            pinnedMessageId = pinCoordinator.pinnedMessageId,
            pinnedAt = pinCoordinator.pinnedAt,
            pinnedByUserId = pinCoordinator.pinnedByUserId,
            pinnedMessage = pinCoordinator.pinnedMessage,
        )
        pinCoordinator.pinInFlight = true
        pinCoordinator.prepareOptimisticUnpinOne(trimmedId, stableMessages)
        bumpPinUi()
        scope.launch {
            teamsRepository.unpinOneForumTopicMessage(teamId, topicId, trimmedId)
                .onSuccess { topic ->
                    pinCoordinator.pinInFlight = false
                    if (topic.pinnedMessageId.isNullOrBlank()) {
                        pinCoordinator.onUnpinSuccess(topic, stableMessages)
                    } else {
                        pinCoordinator.onPinSuccess(topic, stableMessages)
                    }
                    onTopicSnapshotUpdate(topic)
                    bumpPinUi()
                    pinNotice = res.getString(R.string.forum_pinned_toast_unpinned)
                }
                .onFailure { e ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.rollbackTo(snapshot, stableMessages)
                    bumpPinUi()
                    pinNotice = e.toUserMessageRu(res)
                }
        }
    }

    fun unpinForumTopic() {
        if (pinCoordinator.pinInFlight) return
        val snapshot = TopicPinSnapshot(
            pinnedMessageId = pinCoordinator.pinnedMessageId,
            pinnedAt = pinCoordinator.pinnedAt,
            pinnedByUserId = pinCoordinator.pinnedByUserId,
            pinnedMessage = pinCoordinator.pinnedMessage,
        )
        pinCoordinator.pinInFlight = true
        pinCoordinator.prepareOptimisticUnpin(stableMessages)
        bumpPinUi()
        scope.launch {
            teamsRepository.pinForumTopicMessage(teamId, topicId, null)
                .onSuccess { topic ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.onUnpinSuccess(topic, stableMessages)
                    onTopicSnapshotUpdate(topic)
                    bumpPinUi()
                    pinNotice = res.getString(R.string.forum_pinned_toast_unpinned)
                }
                .onFailure { e ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.rollbackTo(snapshot, stableMessages)
                    bumpPinUi()
                    pinNotice = e.toUserMessageRu(res)
                }
        }
    }

    fun applyEdited(msg: TeamForumMessageDto) {
        val i = messages.indexOfFirst { it.id == msg.id }
        if (i >= 0) {
            messages[i] = messages[i].mergePreservingForumMedia(msg)
        } else {
            messages.add(msg)
            trimForumMessagesInMemory()
        }
        pinCoordinator.refreshPinAfterMessageEdit(msg.id, stableMessages)
        bumpPinUi()
    }

    fun applyForumMessageReactions(messageId: String, reactions: List<ChatReaction>) {
        val i = messages.indexOfFirst { it.id == messageId }
        if (i < 0) return
        val current = messages[i]
        if (current.reactions == reactions) return
        messages[i] = current.copy(reactions = reactions)
    }

    fun toggleForumReaction(messageId: String, emoji: String) {
        if (messageId.isBlank() || emoji.isBlank()) return
        val i = messages.indexOfFirst { it.id == messageId }
        if (i < 0) return
        val previous = messages[i]
        val optimistic = applyOptimisticForumReactionToggle(previous, emoji)
        if (optimistic === previous) return
        messages[i] = optimistic
        scope.launch {
            teamsRepository.toggleForumMessageReaction(teamId, topicId, messageId, emoji)
                .onSuccess { updated -> applyForumMessageReactions(updated.id, updated.reactions) }
                .onFailure { e ->
                    val rollbackIndex = messages.indexOfFirst { it.id == messageId }
                    if (rollbackIndex >= 0) {
                        messages[rollbackIndex] = previous
                    }
                    error = e.toUserMessageRu(res)
                }
        }
    }

    fun removeMessage(messageId: String) {
        messages.removeAll { it.id == messageId }
        bumpMessagesGeneration()
        if (activeActionMessageId == messageId) {
            activeActionMessageId = null
        }
        selectedMessageIds = selectedMessageIds - messageId
        if (replyToMessage?.id == messageId) replyToMessage = null
        if (editingForumMessage?.id == messageId) editingForumMessage = null
    }

    fun applyDeleted(ev: TeamForumMessageDeletedEvent) {
        removeMessage(ev.messageId)
        pinCoordinator.clearPinIfMessageDeleted(ev.messageId, stableMessages)
        bumpPinUi()
    }

    fun mergeReadCursor(messageId: String) {
        if (messageId.isBlank()) return
        val prev = lastReadCursor
        if (prev != null && !isObjectIdNewer(messageId, prev)) return
        lastReadCursor = messageId
        forumPrefs.setLastReadMessageId(teamId, topicId, messageId)
    }

    var markForumReadJob by remember(teamId, topicId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun scheduleMarkForumTopicRead(messageId: String) {
        mergeReadCursor(messageId)
        markForumReadJob?.cancel()
        markForumReadJob = scope.launch {
            delay(320)
            teamsRepository.markForumTopicRead(teamId, topicId, messageId)
        }
    }

    fun flushPendingMarkForumTopicRead() {
        markForumReadJob?.cancel()
        markForumReadJob = null
        val cursor = lastReadCursor?.trim().orEmpty()
        if (cursor.isBlank()) return
        scope.launch {
            teamsRepository.markForumTopicRead(teamId, topicId, cursor)
        }
    }

    fun leaveTopic() {
        flushPendingMarkForumTopicRead()
        onBack()
    }

    BackHandler { leaveTopic() }

    fun markTopicReadToLatest(forceSync: Boolean = false) {
        val newestId = stableMessages.lastOrNull()?.id ?: return
        val prev = lastReadCursor
        if (!forceSync && prev != null && !isObjectIdNewer(newestId, prev)) return
        scope.launch {
            teamsRepository.markForumTopicRead(teamId, topicId, newestId)
                .onSuccess { mergeReadCursor(newestId) }
        }
    }

    fun markVisibleForumMessages(lazyIndices: List<Int>) {
        if (!overlayUi) return
        val self = currentUserId.trim()
        val lastRead = lastReadCursor?.trim().orEmpty()
        var watermark: String? = null
        for (lazyIdx in lazyIndices) {
            val timelineIndex = listDerived.timeline.lastIndex - lazyIdx
            val entry = listDerived.timeline.getOrNull(timelineIndex) ?: continue
            val id = when (entry) {
                is ForumTimelineEntry.Message -> entry.messageId
                else -> continue
            }
            if (id.isBlank()) continue
            if (self.isNotBlank()) {
                val sender = stableMessages.find { it.id == id }?.senderUserId?.trim()
                if (sender == self) continue
            }
            if (lastRead.isNotEmpty() && !isObjectIdNewer(id, lastRead)) continue
            watermark = when (val prev = watermark) {
                null -> id
                else -> if (isObjectIdNewer(id, prev)) id else prev
            }
        }
        val markId = watermark ?: return
        scheduleMarkForumTopicRead(markId)
    }

    val markReadTopicKey = forumMarkReadTopicKey(topicId)
    LaunchedEffect(teamId, topicId, stableMessages.lastOrNull()?.id) {
        onProvideMarkReadAction(markReadTopicKey) {
            scope.launch {
                val newestId = stableMessages.lastOrNull()?.id
                if (!newestId.isNullOrBlank()) {
                    markTopicReadToLatest(forceSync = true)
                } else {
                    TeamForumMarkRead.markTopicReadToLatest(
                        teamsRepository = teamsRepository,
                        forumPrefs = forumPrefs,
                        teamId = teamId,
                        topicId = topicId,
                    )
                }
            }
        }
    }
    DisposableEffect(markReadTopicKey) {
        onDispose { onProvideMarkReadAction(markReadTopicKey, null) }
    }

    fun mergeNew(msg: TeamForumMessageDto) {
        val i = messages.indexOfFirst { it.id == msg.id }
        if (i >= 0) {
            messages[i] = messages[i].mergePreservingForumMedia(msg)
        } else {
            messages.add(msg)
            trimForumMessagesInMemory()
        }
        if (!overlayUi || isNearBottom) {
            markTopicReadToLatest()
        }
    }

    fun canDeleteForumMessage(msg: TeamForumMessageDto): Boolean {
        val deleted = !msg.deletedAt.isNullOrBlank() &&
            !msg.deletedAt.equals("null", ignoreCase = true)
        if (deleted) return false
        return msg.senderUserId == currentUserId || canModerateMessages
    }

    fun uploadPickedApk(uri: Uri, displayName: String) {
        scope.launch {
            uploadingFile = true
            error = null
            pendingApkLabel = displayName.trim().ifBlank { "update.apk" }
            pickedImageUris = emptyList()
            attachmentPreviewStartIndex = null
            val result = uploadForumApkFromUri(context, res, teamsRepository, teamId, uri, displayName)
            uploadingFile = false
            result.onSuccess { (fileId, label) ->
                pendingApkFileId = fileId
                pendingApkLabel = label
            }
            result.onFailure { e ->
                pendingApkFileId = null
                pendingApkLabel = null
                error = e.toUserMessageRu(res)
            }
        }
    }

    val pickApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                uploadPickedApk(uri, queryDisplayName(context, uri))
            }
        },
    )

    suspend fun loadOlderForumPage(): Boolean {
        if (!hasMoreOlder || loadingOlder) return false
        val oldestId = messages.firstOrNull()?.id?.trim().orEmpty()
        if (oldestId.isEmpty()) return false
        loadingOlder = true
        return try {
            val page = teamsRepository.listForumMessages(teamId, topicId, before = oldestId, limit = 50)
                .getOrElse { return false }
            val visible = page.filter { m ->
                m.deletedAt.isNullOrBlank() ||
                    m.deletedAt.equals("null", ignoreCase = true)
            }
            val existingIds = messages.asSequence().map { it.id }.toHashSet()
            val older = visible.filter { it.id !in existingIds }
            messages.addAll(0, older)
            trimForumMessagesInMemory()
            hasMoreOlder = page.size >= 50 && messages.isNotEmpty()
            true
        } finally {
            loadingOlder = false
        }
    }

    fun jumpToFirstUnreadInTopic() {
        val lastRead = lastReadCursor?.trim().orEmpty()
        if (lastRead.isEmpty()) return
        val self = currentUserId.trim()
        val targetId = stableMessages.lastOrNull { msg ->
            val id = msg.id.trim()
            if (id.isEmpty()) return@lastOrNull false
            if (self.isNotBlank() && msg.senderUserId.trim() == self) return@lastOrNull false
            isObjectIdNewer(id, lastRead)
        }?.id ?: return
        scope.launch {
            jumpToForumPinnedMessage(
                messageId = targetId,
                messageIdsOldestFirst = { stableMessages.map { it.id } },
                hasMoreOlder = { hasMoreOlder },
                isLoadingOlder = { loadingOlder },
                loadOlder = { loadOlderForumPage() },
                timelineIndexForMessageId = { id ->
                    forumLazyIndexForMessageId(stableMessages, listDerived, id)
                },
                scrollToTimelineIndex = { idx ->
                    runCatching { listState.scrollTimelineItemToViewportCenter(idx) }
                        .onFailure { listState.scrollToItem(idx) }
                },
                onHighlight = { id -> highlightMessageId = id },
            )
        }
    }

    fun visibleForumMessages(page: List<TeamForumMessageDto>): List<TeamForumMessageDto> =
        page.filter { m ->
            m.deletedAt.isNullOrBlank() ||
                m.deletedAt.equals("null", ignoreCase = true)
        }

    fun loadForumMessages(before: String?, appendOlder: Boolean) {
        scope.launch {
            if (appendOlder) {
                loadingOlder = true
            } else {
                error = null
                val diskSnapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (currentUserId.isNotBlank()) {
                        app.launchDiskCache.loadForumMessages(currentUserId, teamId, topicId)
                    } else {
                        null
                    }
                }
                if (diskSnapshot != null) {
                    messages.clear()
                    messages.addAll(visibleForumMessages(diskSnapshot.messages))
                    trimForumMessagesInMemory()
                    hasMoreOlder = diskSnapshot.hasMoreOlder
                    loading = false
                } else {
                    loading = true
                }
            }
            teamsRepository.listForumMessages(teamId, topicId, before = before, limit = 50)
                .onSuccess { page ->
                    val visible = visibleForumMessages(page)
                    if (appendOlder) {
                        val existingIds = messages.asSequence().map { it.id }.toHashSet()
                        val older = visible.filter { it.id !in existingIds }
                        messages.addAll(0, older)
                        trimForumMessagesInMemory()
                    } else {
                        messages.clear()
                        messages.addAll(visible)
                        trimForumMessagesInMemory()
                    }
                    hasMoreOlder = page.size >= 50 && messages.isNotEmpty()
                    if (!appendOlder && currentUserId.isNotBlank() && messages.isNotEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            app.launchDiskCache.saveForumMessages(
                                currentUserId,
                                teamId,
                                topicId,
                                messages.toList(),
                                hasMoreOlder,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    if (!appendOlder && messages.isEmpty()) error = e.toUserMessageRu(res)
                }
            loading = false
            loadingOlder = false
        }
    }

    LaunchedEffect(teamId, topicId) {
        clearPendingAttachment()
        draft = ""
        loadForumMessages(before = null, appendOlder = false)
    }

    LaunchedEffect(teamId, topicId) {
        lastReadCursor = forumPrefs.getLastReadMessageId(teamId, topicId)
    }

    LaunchedEffect(stableMessages.lastOrNull()?.id) {
        if (!overlayUi && stableMessages.isNotEmpty()) {
            markTopicReadToLatest()
        }
    }

    val topicUnreadEstimate = remember(stableMessages, lastReadCursor, currentUserId) {
        val lastRead = lastReadCursor?.trim().orEmpty()
        val self = currentUserId.trim()
        stableMessages.count { msg ->
            val id = msg.id.trim()
            if (id.isEmpty()) return@count false
            if (self.isNotBlank() && msg.senderUserId.trim() == self) return@count false
            lastRead.isEmpty() || isObjectIdNewer(id, lastRead)
        }
    }
    val firstUnreadMessageId = remember(stableMessages, lastReadCursor, currentUserId) {
        val lastRead = lastReadCursor?.trim().orEmpty()
        val self = currentUserId.trim()
        stableMessages.lastOrNull { msg ->
            val id = msg.id.trim()
            if (id.isEmpty()) return@lastOrNull false
            if (self.isNotBlank() && msg.senderUserId.trim() == self) return@lastOrNull false
            lastRead.isEmpty() || isObjectIdNewer(id, lastRead)
        }?.id
    }
    val firstUnreadLazyIndex = remember(firstUnreadMessageId, listDerived) {
        firstUnreadMessageId?.let { listDerived.fullLazyIndexForMessageId(it) } ?: -1
    }
    val isFirstUnreadVisible by remember(listState, firstUnreadLazyIndex) {
        derivedStateOf {
            if (firstUnreadLazyIndex < 0) return@derivedStateOf true
            listState.layoutInfo.visibleItemsInfo.any { it.index == firstUnreadLazyIndex }
        }
    }
    val showJumpToUnreadMessages by remember(
        overlayUi,
        topicUnreadEstimate,
        firstUnreadLazyIndex,
        isFirstUnreadVisible,
        isNearBottom,
    ) {
        derivedStateOf {
            overlayUi &&
                topicUnreadEstimate > 0 &&
                firstUnreadLazyIndex >= 0 &&
                isNearBottom &&
                !isFirstUnreadVisible
        }
    }
    val markVisibleForumRef = rememberUpdatedState(::markVisibleForumMessages)
    LaunchedEffect(listState, overlayUi, teamId, topicId) {
        if (!overlayUi) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .debounce(140)
            .collect { indices ->
                markVisibleForumRef.value(indices)
            }
    }

    var initialScrollApplied by remember(teamId, topicId) { mutableStateOf(false) }
    LaunchedEffect(stableMessages.lastOrNull()?.id, isNearBottom) {
        val newestId = stableMessages.lastOrNull()?.id ?: return@LaunchedEffect
        if (newestId != lastCountedNewestId) {
            lastCountedNewestId = newestId
            if (!isNearBottom && initialScrollApplied) {
                newMessagesWhileScrolledUp = (newMessagesWhileScrolledUp + 1).coerceAtMost(999)
            }
        }
    }
    LaunchedEffect(loadingOlder) {
        if (loadingOlder && pendingForumScrollAnchor == null) {
            pendingForumScrollAnchor = ForumScrollAnchor(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                timelineSize = timelineSize,
            )
        }
    }

    LaunchedEffect(loadingOlder, timelineSize) {
        if (loadingOlder) return@LaunchedEffect
        val anchor = pendingForumScrollAnchor ?: return@LaunchedEffect
        pendingForumScrollAnchor = null
        val delta = timelineSize - anchor.timelineSize
        if (delta > 0) {
            listState.scrollToItem(
                anchor.firstVisibleItemIndex + delta,
                anchor.firstVisibleItemScrollOffset,
            )
        }
    }

    val hasMoreOlderRef = rememberUpdatedState(hasMoreOlder)
    val loadingOlderRef = rememberUpdatedState(loadingOlder)
    val loadingRef = rememberUpdatedState(loading)

    LaunchedEffect(listState, teamId, topicId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastIdx = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            ForumLoadOlderSignal(lastIdx, total)
        }
            .distinctUntilChanged()
            .debounce(48)
            .collect { sig ->
                if (listState.isScrollInProgress) return@collect
                if (sig.totalItems > 4 &&
                    sig.lastVisibleIndex >= sig.totalItems - 2 &&
                    hasMoreOlderRef.value &&
                    !loadingOlderRef.value &&
                    !loadingRef.value
                ) {
                    val oldestId = stableMessages.firstOrNull()?.id
                    if (oldestId != null) {
                        loadForumMessages(before = oldestId, appendOlder = true)
                    }
                }
            }
    }

    LaunchedEffect(stableMessages.lastOrNull()?.id, listDerived.timeline.size) {
        if (stableMessages.isEmpty() || listDerived.timeline.isEmpty()) return@LaunchedEffect
        if (!initialScrollApplied) {
            initialScrollApplied = true
            runCatching {
                listState.scrollReverseChatRevealLatest(animate = false, adjustViewport = false)
            }
            return@LaunchedEffect
        }
        if (isNearBottom && !listState.isScrollInProgress) {
            runCatching {
                listState.scrollReverseChatRevealLatest(animate = false, adjustViewport = false)
            }
        }
    }

    LaunchedEffect(typingHint) {
        val hint = typingHint ?: return@LaunchedEffect
        delay(4000)
        if (typingHint == hint) typingHint = null
    }

    DisposableEffect(teamId, topicId, sectionActive) {
        if (!sectionActive) {
            onDispose { }
        } else {
            val onNew: (TeamForumMessageDto) -> Unit = { mergeNew(it) }
            val onEdited: (TeamForumMessageDto) -> Unit = { applyEdited(it) }
            val onDeleted: (TeamForumMessageDeletedEvent) -> Unit = { applyDeleted(it) }
            val onTyping: (TeamForumTypingEvent) -> Unit = { ev ->
                if (ev.userId != currentUserId) {
                    typingHint = context.getString(R.string.team_forum_typing, ev.username)
                }
            }
            val onPin: (TeamForumTopicPinChangedEvent) -> Unit = { applyTopicPin(it) }
            val onReaction: (TeamForumMessageReactionEvent) -> Unit = { ev ->
                if (ev.teamId == teamId && ev.topicId == topicId) {
                    applyForumMessageReactions(ev.messageId, ev.reactions)
                }
            }
            forumSocket.addMessageListener(onNew)
            forumSocket.addMessageEditedListener(onEdited)
            forumSocket.addMessageDeletedListener(onDeleted)
            forumSocket.addMessageReactionListener(onReaction)
            forumSocket.addTypingListener(onTyping)
            forumSocket.addTopicPinChangedListener(onPin)
            forumSocket.connect(
                BuildConfig.API_BASE_URL,
                teamId,
                topicId,
            ) { tokenStore.getAccessToken() }
            onDispose {
                if (overlayUi) {
                    markForumReadJob?.cancel()
                    val cursor = lastReadCursor?.trim().orEmpty()
                    if (cursor.isNotEmpty()) {
                        scope.launch {
                            teamsRepository.markForumTopicRead(teamId, topicId, cursor)
                        }
                    }
                } else {
                    markTopicReadToLatest(forceSync = true)
                }
                forumSocket.removeMessageListener(onNew)
                forumSocket.removeMessageEditedListener(onEdited)
                forumSocket.removeMessageDeletedListener(onDeleted)
                forumSocket.removeMessageReactionListener(onReaction)
                forumSocket.removeTypingListener(onTyping)
                forumSocket.removeTopicPinChangedListener(onPin)
                forumSocket.connectTeamInbox(
                    BuildConfig.API_BASE_URL,
                    teamId,
                ) { tokenStore.getAccessToken() }
            }
        }
    }

    val forumListContentPadding = if (overlayUi) {
        PaddingValues(
            start = SquadRelayDimens.contentPaddingHorizontal,
            end = SquadRelayDimens.contentPaddingHorizontal,
            top = 52.dp,
            bottom = 10.dp,
        )
    } else {
        PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    }

    CompositionLocalProvider(
        LocalOpenRemoteChatImagePreview provides openImages,
    ) {
    Column(Modifier.fillMaxSize()) {
        error?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        typingHint?.let { hint ->
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        pinNotice?.let { notice ->
            Text(
                notice,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
            LaunchedEffect(notice) {
                delay(2500)
                if (pinNotice == notice) pinNotice = null
            }
        }
        val pinBarPreview = remember(pinRevision) { pinCoordinator.pinBarPreview }
        val pinHistoryCount = remember(pinRevision) { pinCoordinator.pinHistoryCount }
        val pinMessageId = remember(pinRevision) { pinCoordinator.pinnedMessageId }
        val pinnedYouLabel = stringResource(R.string.chat_pinned_meta_you)
        val pinnedMetaLine = remember(pinRevision, pinBarPreview) {
            formatPinnedMetaLine(
                pinnedAt = pinCoordinator.pinnedAt,
                pinnedByUsername = pinBarPreview?.pinnedByUsername,
                pinnedByUserId = pinCoordinator.pinnedByUserId,
                currentUserId = currentUserId,
                youLabel = pinnedYouLabel,
                userTemplate = { name -> res.getString(R.string.chat_pinned_meta_user, name) },
                formatTime = { formatForumTopicTimeRu(it) },
            )
        }
        val pinBarDismissed = remember(pinRevision, pinMessageId) {
            val pinId = pinCoordinator.pinnedMessageId?.trim().orEmpty()
            pinId.isNotEmpty() &&
                pinHistoryPrefs.isPinBarDismissed(pinHistoryPrefs.forumScopeKey(teamId, topicId), pinId)
        }
        if (pinMessageId != null && !pinBarDismissed) {
            pinBarPreview?.let { preview ->
            val pinnedDeleted = isForumPinnedPreviewLikelyDeleted(
                preview = preview,
                messages = stableMessages,
                serverPreview = pinCoordinator.pinnedMessage,
                pinnedMessageId = pinCoordinator.pinnedMessageId,
            )
            val pinnedUnavailable = isForumPinnedPreviewUnavailable(
                preview = preview,
                messages = stableMessages,
                serverPreview = pinCoordinator.pinnedMessage,
                pinnedMessageId = pinCoordinator.pinnedMessageId,
            )
            PinnedMessageBar(
                preview = preview,
                canUnpin = canModerateMessages,
                onTap = {
                    scope.launch {
                        val targetId = preview.id.trim().ifEmpty {
                            pinCoordinator.pinnedMessageId?.trim().orEmpty()
                        }
                        if (targetId.isEmpty()) return@launch
                        val jumped = jumpToForumPinnedMessage(
                            messageId = targetId,
                            messageIdsOldestFirst = { stableMessages.map { it.id } },
                            hasMoreOlder = { hasMoreOlder },
                            isLoadingOlder = { loadingOlder },
                            loadOlder = { loadOlderForumPage() },
                            timelineIndexForMessageId = { id ->
                                forumLazyIndexForMessageId(stableMessages, listDerived, id)
                            },
                            scrollToTimelineIndex = { idx ->
                                runCatching { listState.scrollTimelineItemToViewportCenter(idx) }
                                    .onFailure { listState.scrollToItem(idx) }
                            },
                            onHighlight = { id -> highlightMessageId = id },
                        )
                        if (!jumped) {
                            pinNotice = res.getString(R.string.chat_jump_quote_not_found)
                        } else {
                            delay(900)
                            if (highlightMessageId == targetId) highlightMessageId = null
                            if (pinCoordinator.pinHistoryCount > 1) {
                                pinCoordinator.advancePinBarIndex(stableMessages)
                                bumpPinUi()
                            }
                        }
                    }
                },
                onUnpin = {
                    val pinId = pinBarPreview.id.trim()
                        .ifEmpty { pinCoordinator.pinnedMessageId?.trim().orEmpty() }
                    if (pinId.isNotEmpty()) {
                        unpinOneForumMessage(pinId)
                    } else {
                        unpinForumTopic()
                    }
                },
                historyCount = pinHistoryCount,
                messageDeleted = pinnedDeleted,
                messageUnavailable = pinnedUnavailable,
                thumbnailUrl = preview.resolvedThumbnailUrl(),
                pinnedMetaLine = pinnedMetaLine,
                onLongPress = { showForumPinnedSheet = true },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            }
        }
        PinnedMessagesSheet(
            visible = showForumPinnedSheet,
            items = pinCoordinator.pinnedMessages.ifEmpty {
                pinBarPreview?.let { listOf(it) } ?: emptyList()
            },
            canModerate = canModerateMessages,
            activePinId = pinCoordinator.pinnedMessageId,
            onDismiss = { showForumPinnedSheet = false },
            onJumpTo = { messageId ->
                scope.launch {
                    val jumped = jumpToForumPinnedMessage(
                        messageId = messageId,
                        messageIdsOldestFirst = { stableMessages.map { it.id } },
                        hasMoreOlder = { hasMoreOlder },
                        isLoadingOlder = { loadingOlder },
                        loadOlder = { loadOlderForumPage() },
                        timelineIndexForMessageId = { mid ->
                            forumLazyIndexForMessageId(stableMessages, listDerived, mid)
                        },
                        scrollToTimelineIndex = { idx ->
                            runCatching { listState.scrollTimelineItemToViewportCenter(idx) }
                                .onFailure { listState.scrollToItem(idx) }
                        },
                        onHighlight = { mid -> highlightMessageId = mid },
                    )
                    if (!jumped) {
                        pinNotice = res.getString(R.string.chat_jump_quote_not_found)
                    }
                }
            },
            onUnpinOne = { unpinOneForumMessage(it) },
            onUnpinAll = { unpinForumTopic() },
            onHideBar = {
                val pinId = pinCoordinator.pinnedMessageId?.trim().orEmpty()
                if (pinId.isNotEmpty()) {
                    pinHistoryPrefs.setDismissedPinBar(pinHistoryPrefs.forumScopeKey(teamId, topicId), pinId)
                    bumpPinUi()
                }
                showForumPinnedSheet = false
            },
            messageStateFor = { preview ->
                forumPinPreviewDisplayState(
                    preview = preview,
                    messages = stableMessages,
                    serverPreview = pinCoordinator.pinnedMessage,
                    pinnedMessageId = pinCoordinator.pinnedMessageId,
                )
            },
        )
        if (selectedMessageIds.isNotEmpty()) {
            ForumSelectionToolbar(
                selectedCount = selectedMessageIds.size,
                isDeleting = deletingSelection,
                onClear = { if (!deletingSelection) selectedMessageIds = emptySet() },
                onDelete = {
                    if (!deletingSelection) {
                        OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                        confirmBulkDelete = true
                    }
                },
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (loading && messages.isEmpty()) {
                CenteredScreenLoading()
            } else {
                val configuration = LocalConfiguration.current
                val listBubbleMaxWidth = remember(configuration.screenWidthDp) {
                    minOf(
                        configuration.screenWidthDp.dp * ChatBubbleMaxWidthFraction,
                        ChatBubbleMaxWidthCap,
                    )
                }
                val expandScrollCompensation = remember(listState, scope) {
                    { deltaPx: Int ->
                        scope.launch {
                            listState.scrollReverseChatCompensateExpand(deltaPx)
                        }
                        Unit
                    }
                }
                CompositionLocalProvider(
                    LocalChatBubbleMaxWidth provides listBubbleMaxWidth,
                    LocalMessageExpandScrollCompensation provides expandScrollCompensation,
                ) {
                ForumTopicMessagesLazyList(
                    modifier = Modifier.fillMaxSize(),
                    messages = stableMessages,
                    listDerived = listDerived,
                    listState = listState,
                    hasMoreOlder = hasMoreOlder,
                    loadingOlder = loadingOlder,
                    highlightMessageId = highlightMessageId,
                    contentPadding = forumListContentPadding,
                ) { msg, idx ->
                    val mine = msg.senderUserId == currentUserId
                    val inSelectionMode = selectedMessageIds.isNotEmpty()
                    val isSelected = msg.id in selectedMessageIds
                    val canDeleteMsg = canDeleteForumMessage(msg)
                    ForumMessageBubble(
                        message = msg,
                        teamId = teamId,
                        topicId = topicId,
                        cluster = listDerived.clusterFlags.getOrNull(idx),
                        isMine = mine,
                        canDelete = canDeleteMsg,
                        inSelectionMode = inSelectionMode,
                        isSelected = isSelected,
                        highlighted = LocalChatHighlightMessageId.current == msg.id,
                        onJumpToMessage = { targetId ->
                            val lazyIdx = listDerived.fullLazyIndexForMessageId(targetId)
                            if (lazyIdx != null) {
                                scope.launch {
                                    runCatching { listState.scrollTimelineItemToViewportCenter(lazyIdx) }
                                        .onFailure { listState.scrollToItem(lazyIdx) }
                                    highlightMessageId = targetId
                                    delay(900)
                                    if (highlightMessageId == targetId) highlightMessageId = null
                                }
                            }
                        },
                        onToggleSelection = { id ->
                            selectedMessageIds =
                                if (id in selectedMessageIds) selectedMessageIds - id else selectedMessageIds + id
                        },
                        onBeginSelection = { id ->
                            selectedMessageIds = setOf(id)
                            dismissMessageActions()
                        },
                        onSwipeReply = {
                            if (msg.deletedAt.isNullOrBlank()) {
                                replyToMessage = msg
                            }
                        },
                        onOpenActions = { req ->
                            if (msg.deletedAt.isNullOrBlank()) {
                                if (overlayUi) {
                                    OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
                                }
                                activeActionMessageId = req.messageId
                            }
                        },
                        onToggleReaction = { messageId, emoji -> toggleForumReaction(messageId, emoji) },
                        downloadingForumFileUrl = downloadingForumFileUrl,
                        onDownloadForumFile = { forumMsg ->
                            val url = forumMsg.fileRelativeUrl?.trim().orEmpty()
                            if (url.isNotBlank() && downloadingForumFileUrl == null) {
                                downloadingForumFileUrl = url
                                scope.launch {
                                    downloadAndInstallForumApk(
                                        context,
                                        url,
                                        forumMsg.fileFilename,
                                    ).onFailure { e ->
                                        error = e.message
                                            ?: res.getString(R.string.chat_apk_download_failed)
                                    }
                                    downloadingForumFileUrl = null
                                }
                            }
                        },
                    )
                }
                }
                if (overlayUi) {
                    ForumTopicOverlayBackChip(
                        onClick = { leaveTopic() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = SquadRelayDimens.contentPaddingHorizontal,
                                top = 8.dp,
                            )
                            .zIndex(5f),
                    )
                    OverlayReactionLogJumpToUnreadFab(
                        visible = showJumpToUnreadMessages,
                        unreadCount = topicUnreadEstimate,
                        onClick = { jumpToFirstUnreadInTopic() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .zIndex(6f),
                    )
                }
                ChatScrollToLatestFab(
                    visible = showScrollToLatestFab,
                    newMessageCount = newMessagesWhileScrolledUp,
                    onClick = {
                        newMessagesWhileScrolledUp = 0
                        lastCountedNewestId = stableMessages.lastOrNull()?.id
                        scope.launch {
                            runCatching {
                                listState.scrollReverseChatRevealLatest(animate = true)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                )
            }
        }
        val forumReplyChat = remember(replyToMessage, teamId, topicId) {
            replyToMessage?.toDisplayChatMessage(teamId, topicId)
        }
        val forumEditingChat = remember(editingForumMessage, teamId, topicId) {
            editingForumMessage?.toDisplayChatMessage(teamId, topicId)
        }
        ChatComposerBar {
        ChatComposer(
            draft = draft,
            pickedImageUris = pickedImageUris,
            replyToMessage = forumReplyChat,
            editingMessage = forumEditingChat,
            isSending = sending,
            sendEnabled = true,
            readOnly = uploadingImage || uploadingFile,
            enabledStickerPackKeys = enabledStickerPackKeys,
            onDraftChange = {
                draft = it
                forumSocket.emitTyping()
            },
            onSendDraft = {
                scope.launch {
                    val editing = editingForumMessage
                    if (editing != null) {
                        val trimmed = draft.trim()
                        if (trimmed.isEmpty() &&
                            editing.imageRelativeUrls.isEmpty() &&
                            editing.imageRelativeUrl.isNullOrBlank()
                        ) {
                            return@launch
                        }
                        if (sending) return@launch
                        sending = true
                        teamsRepository.patchForumMessage(teamId, topicId, editing.id, trimmed)
                            .onSuccess {
                                applyEdited(it)
                                draft = ""
                                editingForumMessage = null
                            }
                            .onFailure { e -> error = e.toUserMessageRu(res) }
                        sending = false
                        return@launch
                    }
                    val trimmed = draft.trim()
                    val urisToUpload = pickedImageUris
                    if (trimmed.isEmpty() && urisToUpload.isEmpty() && pendingApkFileId == null) {
                        return@launch
                    }
                    if (uploadingImage || uploadingFile || sending) return@launch
                    sending = true
                    var imageFileIds: List<String>? = null
                    if (urisToUpload.isNotEmpty()) {
                        uploadingImage = true
                        uploadForumImagesFromUris(context, res, teamsRepository, teamId, urisToUpload)
                            .onSuccess { (ids, _) -> imageFileIds = ids.takeIf { it.isNotEmpty() } }
                            .onFailure { e ->
                                error = e.toUserMessageRu(res)
                                sending = false
                                uploadingImage = false
                                return@launch
                            }
                        uploadingImage = false
                    }
                    teamsRepository.postForumMessage(
                        teamId,
                        topicId,
                        trimmed,
                        replyToMessageId = replyToMessage?.id,
                        imageFileId = null,
                        imageFileIds = imageFileIds,
                        fileFileId = pendingApkFileId,
                    )
                        .onSuccess {
                            mergeNew(it)
                            draft = ""
                            replyToMessage = null
                            clearPendingAttachment()
                        }
                        .onFailure { e -> error = e.toUserMessageRu(res) }
                    sending = false
                }
            },
            onSendStickerPayload = { payload ->
                scope.launch {
                    sending = true
                    clearPendingAttachment()
                    teamsRepository.postForumMessage(
                        teamId = teamId,
                        topicId = topicId,
                        text = payload,
                        replyToMessageId = replyToMessage?.id,
                        imageFileId = null,
                        imageFileIds = null,
                    )
                        .onSuccess { mergeNew(it) }
                        .onFailure { e -> error = e.toUserMessageRu(res) }
                    sending = false
                    replyToMessage = null
                }
            },
            onPickImages = { uris, append ->
                val stable = stabilizeComposerImageUris(context, uris)
                if (stable.isEmpty()) return@ChatComposer
                pickedImageUris = if (append) {
                    (pickedImageUris + stable).distinctBy { it.toString() }
                } else {
                    stable.distinctBy { it.toString() }
                }.take(12)
            },
            onRemovePickedImage = { uri ->
                pickedImageUris = pickedImageUris.filterNot { it == uri }
            },
            onClearPickedImages = { clearPendingAttachment() },
            onClearReply = {
                replyToMessage = null
            },
            onClearEdit = {
                editingForumMessage = null
                draft = ""
            },
            onOpenAttachmentPreview = { idx -> attachmentPreviewStartIndex = idx },
            pendingApkLabel = pendingApkLabel,
            onClearPendingApk = { clearPendingAttachment() },
            onPickApk = if (canModerateMessages) {
                {
                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                    pickApkLauncher.launch("application/*")
                }
            } else {
                null
            },
            hasReadyFileAttachment = !pendingApkFileId.isNullOrBlank(),
            isUploadingFile = uploadingFile,
        )
        }
    }

    remoteImagePreview?.let { (urls, start) ->
        if (urls.isNotEmpty()) {
            MessengerImagesPreviewHost(
                urls = urls,
                startIndex = start,
                onDismiss = { remoteImagePreview = null },
            )
        }
    }
    attachmentPreviewStartIndex?.let { start ->
        if (pickedImageUris.isNotEmpty()) {
            AttachmentPreviewOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(8f),
                uris = pickedImageUris,
                startIndex = start,
                onDismiss = { attachmentPreviewStartIndex = null },
                onOpenExternal = { uri ->
                    val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/*")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val resolved = context.packageManager.resolveActivity(
                        viewIntent,
                        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                    )
                    if (resolved != null) {
                        context.startActivity(viewIntent)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.chat_open_external_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onRemove = { uri ->
                    pickedImageUris = pickedImageUris.filterNot { it == uri }
                    if (pickedImageUris.size <= 1) {
                        attachmentPreviewStartIndex = null
                    } else {
                        attachmentPreviewStartIndex =
                            attachmentPreviewStartIndex?.coerceAtMost(pickedImageUris.lastIndex)
                    }
                },
            )
        }
    }

    // legacy menu dialog removed (replaced with context menu popup)

    pendingForumPinMessage?.let { pinTarget ->
        val pinTargetId = pinTarget.id
        OverlayModalScope {
            OverlayAwareAlertDialog(
                onDismissRequest = { pendingForumPinMessage = null },
                title = { Text(stringResource(R.string.forum_pin_replace_title)) },
                text = { Text(stringResource(R.string.forum_pin_replace_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pinForumMessage(pinTargetId, pinTarget)
                            pendingForumPinMessage = null
                        },
                    ) {
                        Text(stringResource(R.string.chat_pin_replace_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingForumPinMessage = null }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }
    }

    val activeActionMessage = remember(activeActionMessageId, messages) {
        activeActionMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    }
    if (selectedMessageIds.isEmpty()) {
        activeActionMessage?.let { msg ->
            val menuCanPin = canModerateMessages && msg.deletedAt.isNullOrBlank()
            val isTopicPinnedMessage =
                pinCoordinator.pinnedMessageId != null && msg.id == pinCoordinator.pinnedMessageId
            val menuImageUrls = remember(msg.id, msg.imageRelativeUrl, msg.imageRelativeUrls) {
                buildList {
                    msg.imageRelativeUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
                        add(resolvedChatAttachmentImageUrl(it))
                    }
                    msg.imageRelativeUrls.forEach { raw ->
                        val t = raw.trim()
                        if (t.isNotBlank()) add(resolvedChatAttachmentImageUrl(t))
                    }
                }.distinct()
            }
            val menuHasImages = menuImageUrls.isNotEmpty()
            val menuHasMapCoordinate = remember(msg.text) {
                com.lastasylum.alliance.game.MapCoordinateParser.parse(msg.text) != null
            }
            val menuMayEdit = (msg.senderUserId == currentUserId || canModerateMessages) &&
                msg.deletedAt.isNullOrBlank() &&
                (
                    msg.text.isNotBlank() ||
                        msg.imageRelativeUrls.isNotEmpty() ||
                        !msg.imageRelativeUrl.isNullOrBlank()
                    )
            val menuScope: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().zIndex(6f)) {
                    MessageContextMenuScrim(onDismiss = dismissMessageActions)
                    MessageContextMenuPopup(
                        showReactions = true,
                        canCopy = forumMessageHasMenuCopyAction(msg),
                        canPin = menuCanPin,
                        isPinned = isTopicPinnedMessage,
                        pinActionsEnabled = !pinCoordinator.pinInFlight,
                        mayEdit = menuMayEdit,
                        hasImages = menuHasImages,
                        hasMapCoordinate = menuHasMapCoordinate,
                        onDismiss = dismissMessageActions,
                        actions = MessageContextMenuActions(
                            onReply = {
                                replyToMessage = msg
                                editingForumMessage = null
                                dismissMessageActions()
                            },
                            onCopy = {
                                copyForumMessageToClipboard(context, msg)
                                dismissMessageActions()
                            },
                            onPin = {
                                val existingPin = pinCoordinator.pinnedMessageId?.trim().orEmpty()
                                if (existingPin.isNotEmpty() && existingPin != msg.id) {
                                    pendingForumPinMessage = msg
                                } else {
                                    pinForumMessage(msg.id, msg)
                                }
                            },
                            onUnpin = {
                                unpinOneForumMessage(msg.id)
                                dismissMessageActions()
                            },
                            onEdit = {
                                editingForumMessage = msg
                                draft = msg.text
                                replyToMessage = null
                                clearPendingAttachment()
                            },
                            onReact = { emoji -> toggleForumReaction(msg.id, emoji) },
                            onViewImages = if (menuHasImages) {
                                {
                                    remoteImagePreview = menuImageUrls to 0
                                    dismissMessageActions()
                                }
                            } else {
                                null
                            },
                            onSaveToGallery = if (menuHasImages) {
                                {
                                    val urls = menuImageUrls
                                    dismissMessageActions()
                                    scope.launch {
                                        val result = saveChatImagesToGallery(context, urls)
                                        val toastRes = when {
                                            result.savedCount == 0 ->
                                                R.string.chat_gallery_save_failed_toast
                                            result.failedCount > 0 ->
                                                R.string.chat_gallery_save_partial_toast
                                            else ->
                                                R.string.chat_gallery_saved_toast
                                        }
                                        val text = when (toastRes) {
                                            R.string.chat_gallery_save_partial_toast ->
                                                context.getString(toastRes, result.savedCount, result.totalRequested)
                                            R.string.chat_gallery_saved_toast ->
                                                context.getString(toastRes, result.savedCount)
                                            else ->
                                                context.getString(toastRes)
                                        }
                                        android.widget.Toast.makeText(context.applicationContext, text, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                null
                            },
                            onGoToMap = {
                                com.lastasylum.alliance.game.GameMapNavigator.openFromMessage(context, msg.text)
                                dismissMessageActions()
                            },
                        ),
                    )
                }
            }
            if (overlayUi) {
                OverlayModalScope(preparedByCaller = true) {
                    menuScope()
                }
            } else {
                menuScope()
            }
        }
    }

    if (confirmBulkDelete && selectedMessageIds.isNotEmpty()) {
        OverlayModalScope(preparedByCaller = true) {
        OverlayAwareAlertDialog(
            onDismissRequest = { if (!deletingSelection) confirmBulkDelete = false },
            title = { Text(stringResource(R.string.chat_bulk_delete_title)) },
            text = {
                Text(stringResource(R.string.chat_bulk_delete_body, selectedMessageIds.size))
            },
            confirmButton = {
                TextButton(
                    enabled = !deletingSelection,
                    onClick = {
                        scope.launch {
                            deletingSelection = true
                            val ids = selectedMessageIds.toList()
                            teamsRepository.bulkDeleteForumMessages(teamId, topicId, ids)
                                .onSuccess { result ->
                                    ids.forEach { removeMessage(it) }
                                    ids.forEach { id ->
                                        pinCoordinator.clearPinIfMessageDeleted(id, stableMessages)
                                    }
                                    result.pinChanged?.let { applyTopicPin(it) }
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            selectedMessageIds = emptySet()
                            confirmBulkDelete = false
                            deletingSelection = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.chat_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deletingSelection,
                    onClick = { confirmBulkDelete = false },
                ) { Text(stringResource(R.string.chat_delete_cancel)) }
            },
        )
        }
    }
    }
}

private suspend fun uploadForumImagesFromUris(
    context: android.content.Context,
    res: android.content.res.Resources,
    teamsRepository: TeamsRepository,
    teamId: String,
    uris: List<Uri>,
): Result<Pair<List<String>, String?>> = withContext(Dispatchers.IO) {
    val cr = context.contentResolver
    val cacheDir = context.cacheDir
    val fileIds = mutableListOf<String>()
    var lastPreview: String? = null
    for (uri in uris) {
        val tmp = File.createTempFile("forum_upload_${UUID.randomUUID()}", ".part", cacheDir)
        try {
            val input = openUriInputStream(cr, uri)
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        res.getString(R.string.chat_attachment_read_failed),
                    ),
                )
            input.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
            if (tmp.length() == 0L) continue
            val header = ByteArray(32)
            tmp.inputStream().use { it.read(header) }
            val sniffed = sniffImageMimeFromHeader(header)
            val mime = resolveUploadImageMime(cr.getType(uri)?.trim().orEmpty(), sniffed)
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        res.getString(R.string.chat_attachment_unsupported),
                    ),
                )
            val bytes = tmp.readBytes()
            val name =
                "forum_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.${guessExt(mime)}"
            val uploaded = teamsRepository.uploadForumImage(teamId, bytes, name, mime).getOrElse { err ->
                return@withContext Result.failure(err)
            }
            fileIds.add(uploaded.fileId)
            lastPreview = uploaded.url
        } finally {
            runCatching { tmp.delete() }
        }
    }
    if (fileIds.isEmpty() && uris.isNotEmpty()) {
        return@withContext Result.failure(
            IllegalStateException(res.getString(R.string.chat_attachment_read_failed)),
        )
    }
    Result.success(fileIds to lastPreview)
}

private suspend fun uploadForumApkFromUri(
    context: android.content.Context,
    res: android.content.res.Resources,
    teamsRepository: TeamsRepository,
    teamId: String,
    uri: Uri,
    displayName: String,
): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
    val cr = context.contentResolver
    val safeName = displayName.trim().let { n ->
        if (n.endsWith(".apk", ignoreCase = true)) n else "$n.apk"
    }
    val tmp = File.createTempFile("forum_apk_${UUID.randomUUID()}", ".apk", context.cacheDir)
    try {
        val input = openUriInputStream(cr, uri)
            ?: return@withContext Result.failure(
                IllegalStateException(res.getString(R.string.chat_attachment_read_failed)),
            )
        input.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
        if (tmp.length() == 0L) {
            return@withContext Result.failure(
                IllegalStateException(res.getString(R.string.chat_attachment_prepare_failed)),
            )
        }
        val bytes = tmp.readBytes()
        val uploaded = teamsRepository.uploadForumFile(
            teamId = teamId,
            bytes = bytes,
            fileName = safeName,
            mimeType = "application/vnd.android.package-archive",
        ).getOrElse { return@withContext Result.failure(it) }
        Result.success(uploaded.fileId to safeName)
    } finally {
        runCatching { tmp.delete() }
    }
}

private fun openUriInputStream(cr: ContentResolver, uri: Uri): InputStream? {
    runCatching { cr.openInputStream(uri) }.getOrNull()?.let { return it }
    val pfd = runCatching { cr.openFileDescriptor(uri, "r") }.getOrNull()
    if (pfd != null) {
        runCatching { return ParcelFileDescriptor.AutoCloseInputStream(pfd) }
        runCatching { pfd.close() }
    }
    val afd = runCatching { cr.openAssetFileDescriptor(uri, "r") }.getOrNull()
    if (afd != null) {
        runCatching { return afd.createInputStream() }
        runCatching { afd.close() }
    }
    return null
}

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

private fun sniffImageMimeFromHeader(bytes: ByteArray): String? {
    if (bytes.size < 12) return null
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    if (bytes.hasPrefix(jpeg)) return "image/jpeg"
    val png = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    if (bytes.hasPrefix(png)) return "image/png"
    if (bytes.size >= 6) {
        val gif = String(bytes, 0, 6, Charsets.US_ASCII)
        if (gif == "GIF87a" || gif == "GIF89a") return "image/gif"
    }
    val riff = String(bytes, 0, 4, Charsets.US_ASCII)
    val webp = String(bytes, 8, 4, Charsets.US_ASCII)
    if (riff == "RIFF" && webp == "WEBP") return "image/webp"
    if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "image/bmp"
    if (bytes.size >= 12 &&
        bytes[4] == 'f'.code.toByte() &&
        bytes[5] == 't'.code.toByte() &&
        bytes[6] == 'y'.code.toByte() &&
        bytes[7] == 'p'.code.toByte()
    ) {
        val brand = String(bytes, 8, 4, Charsets.US_ASCII)
        if (brand.equals("heic", ignoreCase = true) ||
            brand.equals("heix", ignoreCase = true) ||
            brand.equals("mif1", ignoreCase = true) ||
            brand.equals("msf1", ignoreCase = true)
        ) {
            return "image/heic"
        }
    }
    return null
}

private fun resolveUploadImageMime(declared: String, sniffed: String?): String? {
    val d = declared.trim()
    val dl = d.lowercase(Locale.ROOT)
    return when {
        dl.startsWith("image/") && dl != "image/*" -> d
        dl == "image/*" -> sniffed ?: "image/jpeg"
        sniffed != null -> sniffed
        else -> null
    }
}

private fun guessExt(mime: String): String = when (mime.lowercase(Locale.ROOT)) {
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/bmp" -> "bmp"
    "image/heic" -> "heic"
    else -> "jpg"
}

@Composable
private fun ForumSelectionToolbar(
    selectedCount: Int,
    isDeleting: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = SquadRelaySurfaces.panelColor(0.52f),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.18f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClear, enabled = !isDeleting) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                }
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, enabled = !isDeleting) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = scheme.error,
                    )
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
    }
}

/** Telegram-like instant feedback before REST round-trip. */
private fun applyOptimisticForumReactionToggle(
    message: TeamForumMessageDto,
    emoji: String,
): TeamForumMessageDto {
    val reactions = message.reactions.toMutableList()
    val at = reactions.indexOfFirst { it.emoji == emoji }
    if (at >= 0) {
        val row = reactions[at]
        if (row.reactedByMe) {
            val nextCount = row.count - 1
            if (nextCount <= 0) {
                reactions.removeAt(at)
            } else {
                reactions[at] = row.copy(count = nextCount, reactedByMe = false)
            }
        } else {
            reactions[at] = row.copy(count = row.count + 1, reactedByMe = true)
        }
    } else {
        reactions.add(
            ChatReaction(
                emoji = emoji,
                count = 1,
                reactedByMe = true,
            ),
        )
    }
    if (reactions == message.reactions) return message
    return message.copy(reactions = reactions)
}
