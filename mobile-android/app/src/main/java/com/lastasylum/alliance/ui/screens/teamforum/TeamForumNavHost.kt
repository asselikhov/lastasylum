package com.lastasylum.alliance.ui.screens.teamforum

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lastasylum.alliance.ui.chat.AttachmentPreviewOverlay
import com.lastasylum.alliance.ui.chat.ChatComposer
import com.lastasylum.alliance.ui.chat.chatComposerAppDock
import com.lastasylum.alliance.ui.chat.stabilizeComposerImageUris
import com.lastasylum.alliance.ui.chat.capForumMessagesOldestFirst
import com.lastasylum.alliance.ui.chat.mergePreservingForumMedia
import com.lastasylum.alliance.ui.chat.ChatScrollToLatestFab
import com.lastasylum.alliance.ui.chat.LocalOpenRemoteChatImagePreview
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
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
import com.lastasylum.alliance.data.teams.TeamForumSocketManager
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamForumTypingEvent
import com.lastasylum.alliance.data.teams.TeamsRepository
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import com.lastasylum.alliance.ui.chat.MessageSheetActionRow
import com.lastasylum.alliance.ui.chat.MessageSheetDividerSpaced
import com.lastasylum.alliance.ui.chat.MessageSheetPreviewSurface
import com.lastasylum.alliance.ui.chat.chatAuthedImageRequest
import com.lastasylum.alliance.ui.chat.replyPreviewText
import com.lastasylum.alliance.ui.util.copyForumMessageToClipboard
import com.lastasylum.alliance.ui.util.forumMessageHasCopyableContent
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayAwareAlertDialog
import com.lastasylum.alliance.overlay.OverlayAwareBottomSheet
import com.lastasylum.alliance.overlay.OverlayInteractionSuppressEffect
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.ui.chat.ChatStickerFormat
import com.lastasylum.alliance.ui.chat.chatDayKey
import com.lastasylum.alliance.ui.chat.formatChatDaySeparator
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.components.PremiumEmptyState
import com.lastasylum.alliance.ui.components.premium.PremiumGradientIconFab
import com.lastasylum.alliance.ui.components.team.ForumTopicFeedCard
import com.lastasylum.alliance.ui.components.team.TeamFeedCardTokens
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
    forumTabReselectSignal: Int = 0,
    /** Wire keys of sticker packs the current user may send. */
    enabledStickerPackKeys: Set<String> = emptySet(),
    onForumInboxChanged: () -> Unit = {},
) {
    val nav = rememberNavController()
    val topicTitles = remember { mutableStateMapOf<String, String>() }
    var listRefreshNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(listRefreshNonce) {
        if (listRefreshNonce > 0) onForumInboxChanged()
    }
    DisposableEffect(teamId) {
        val onTopicActivity: (com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent) -> Unit =
            { event ->
                if (event.senderUserId.trim() != currentUserId.trim()) {
                    listRefreshNonce++
                }
            }
        forumSocket.addTopicActivityListener(onTopicActivity)
        forumSocket.connectTeamInbox(
            com.lastasylum.alliance.BuildConfig.API_BASE_URL,
            teamId,
        ) { tokenStore.getAccessToken() }
        onDispose {
            forumSocket.removeTopicActivityListener(onTopicActivity)
        }
    }
    LaunchedEffect(forumTabReselectSignal) {
        if (forumTabReselectSignal > 0) {
            nav.popBackStack(ForumRoutes.LIST, inclusive = false)
        }
    }
    NavHost(
        navController = nav,
        startDestination = ForumRoutes.LIST,
        modifier = modifier,
    ) {
        composable(ForumRoutes.LIST) {
            TeamForumListRoute(
                teamId = teamId,
                currentUserId = currentUserId,
                canManageTopics = canManageTopics,
                teamsRepository = teamsRepository,
                topicTitles = topicTitles,
                refreshNonce = listRefreshNonce,
                onOpenTopic = { t ->
                    topicTitles[t.id] = t.title
                    nav.navigate(ForumRoutes.topic(t.id))
                },
                onBack = { },
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
            TeamForumTopicChatRoute(
                teamId = teamId,
                topicId = topicId,
                topicTitle = title,
                currentUserId = currentUserId,
                canModerateMessages = canModerateForumMessages,
                teamsRepository = teamsRepository,
                forumSocket = forumSocket,
                tokenStore = tokenStore,
                enabledStickerPackKeys = enabledStickerPackKeys,
                onBack = {
                    nav.popBackStack()
                    listRefreshNonce++
                },
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
    refreshNonce: Int,
    onOpenTopic: (TeamForumTopicDto) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
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
    var searchQuery by remember { mutableStateOf("") }
    var listFilter by remember { mutableStateOf(ForumTopicListFilter.All) }

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
        topics.addAll(
            rows.map { topic ->
                topic.copy(unreadCount = effectiveTopicUnread(topic))
            },
        )
        rows.forEach { t -> topicTitles[t.id] = t.title }
        rows.filter { topic ->
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
            val diskTopics = if (currentUserId.isNotBlank()) {
                app.launchDiskCache.loadForumTopics(currentUserId, teamId)
            } else {
                null
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
                        app.launchDiskCache.saveForumTopics(currentUserId, teamId, it)
                    }
                    applyTopicRows(it)
                }
                .onFailure { e ->
                    if (topics.isEmpty()) error = e.toUserMessageRu(res)
                }
            loading = false
        }
    }

    val displayedTopics = remember(topics.size, searchQuery, listFilter, lastReadByTopic.size) {
        val q = searchQuery.trim()
        topics.filter { topic ->
            val matchesSearch = q.isEmpty() || topic.title.contains(q, ignoreCase = true)
            val matchesFilter = when (listFilter) {
                ForumTopicListFilter.All -> true
                ForumTopicListFilter.Unread -> effectiveTopicUnread(topic) > 0
                ForumTopicListFilter.Recent -> !topic.lastMessageAt.isNullOrBlank()
            }
            matchesSearch && matchesFilter
        }
    }

    LaunchedEffect(teamId, refreshNonce, currentUserId) {
        val uid = currentUserId.trim()
        if (uid.isNotEmpty()) {
            ReadCursorSession.bind(
                app.chatRoomPreferences,
                forumPrefs,
                app.userSettingsPreferences,
                uid,
            )
        }
        forumPrefs.loadAllLastReadMessageIds(teamId).forEach { (topicId, messageId) ->
            mergeTopicReadCursor(topicId, messageId)
        }
        reload()
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
        if (!overlayUi) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.team_forum_search_hint)) },
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = listFilter == ForumTopicListFilter.All,
                    onClick = { listFilter = ForumTopicListFilter.All },
                    label = { Text(stringResource(R.string.team_forum_filter_all)) },
                )
                FilterChip(
                    selected = listFilter == ForumTopicListFilter.Unread,
                    onClick = { listFilter = ForumTopicListFilter.Unread },
                    label = { Text(stringResource(R.string.team_forum_filter_unread)) },
                )
                FilterChip(
                    selected = listFilter == ForumTopicListFilter.Recent,
                    onClick = { listFilter = ForumTopicListFilter.Recent },
                    label = { Text(stringResource(R.string.team_forum_filter_recent)) },
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                loading && topics.isEmpty() -> {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        strokeWidth = 2.dp,
                    )
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
                displayedTopics.isEmpty() -> {
                    PremiumEmptyState(
                        icon = Icons.Outlined.Forum,
                        title = stringResource(R.string.team_forum_search_hint),
                        body = "",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                else -> {
                    val listTopPad = if (overlayUi) 0.dp else 8.dp
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = SquadRelayDimens.contentPaddingHorizontal,
                            end = SquadRelayDimens.contentPaddingHorizontal,
                            top = listTopPad,
                            bottom = 88.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(TeamFeedCardTokens.listSpacing),
                    ) {
                        itemsIndexed(displayedTopics, key = { _, t -> t.id }) { index, t ->
                            ForumTopicFeedCard(
                                topic = t,
                                listIndex = index,
                                messageMeta = t.lastMessageAt?.let { formatForumTopicTimeRu(it) } ?: "—",
                                displayUnreadCount = effectiveTopicUnread(t),
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
                }
            }
            if (canManageTopics) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamForumTopicChatRoute(
    teamId: String,
    topicId: String,
    @Suppress("UNUSED_PARAMETER") topicTitle: String,
    currentUserId: String,
    canModerateMessages: Boolean,
    teamsRepository: TeamsRepository,
    forumSocket: TeamForumSocketManager,
    tokenStore: TokenStore,
    onBack: () -> Unit,
    enabledStickerPackKeys: Set<String> = emptySet(),
) {
    BackHandler { onBack() }

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
    val listDerived = rememberForumMessagesListDerived(stableMessages)
    var newMessagesWhileScrolledUp by remember(teamId, topicId) { mutableIntStateOf(0) }
    var lastCountedNewestId by remember(teamId, topicId) { mutableStateOf<String?>(null) }
    val isNearBottom by remember(listState, listDerived, hasMoreOlder) {
        derivedStateOf {
            val bottomIdx = listDerived.bottomLazyIndex(hasMoreOlder) ?: return@derivedStateOf true
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= bottomIdx - 2
        }
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
    }
    val forumPrefs = remember { AppContainer.from(context).teamForumPreferences }
    var lastReadCursor by remember { mutableStateOf<String?>(null) }

    var editMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var editBody by remember { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var activeActionMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var deletingSelection by remember { mutableStateOf(false) }
    var highlightMessageId by remember { mutableStateOf<String?>(null) }
    var remoteImagePreview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImages = remember {
        { urls: List<String>, idx: Int -> remoteImagePreview = urls to idx }
    }

    fun clearPendingAttachment() {
        pickedImageUris = emptyList()
        attachmentPreviewStartIndex = null
        pendingApkFileId = null
        pendingApkLabel = null
    }

    fun applyEdited(msg: TeamForumMessageDto) {
        val i = messages.indexOfFirst { it.id == msg.id }
        if (i >= 0) {
            messages[i] = messages[i].mergePreservingForumMedia(msg)
        } else {
            messages.add(msg)
            trimForumMessagesInMemory()
        }
    }

    fun removeMessage(messageId: String) {
        messages.removeAll { it.id == messageId }
        if (activeActionMessageId == messageId) activeActionMessageId = null
        selectedMessageIds = selectedMessageIds - messageId
        if (replyToMessage?.id == messageId) replyToMessage = null
        if (editMessage?.id == messageId) editMessage = null
    }

    fun applyDeleted(ev: TeamForumMessageDeletedEvent) {
        removeMessage(ev.messageId)
    }

    fun mergeReadCursor(messageId: String) {
        if (messageId.isBlank()) return
        val prev = lastReadCursor
        if (prev != null && !isObjectIdNewer(messageId, prev)) return
        lastReadCursor = messageId
        forumPrefs.setLastReadMessageId(teamId, topicId, messageId)
    }

    fun markTopicReadToLatest(forceSync: Boolean = false) {
        val newestId = stableMessages.lastOrNull()?.id ?: return
        val prev = lastReadCursor
        if (!forceSync && prev != null && !isObjectIdNewer(newestId, prev)) return
        scope.launch {
            teamsRepository.markForumTopicRead(teamId, topicId, newestId)
                .onSuccess { mergeReadCursor(newestId) }
        }
    }

    fun mergeNew(msg: TeamForumMessageDto) {
        val i = messages.indexOfFirst { it.id == msg.id }
        if (i >= 0) {
            messages[i] = messages[i].mergePreservingForumMedia(msg)
        } else {
            messages.add(msg)
            trimForumMessagesInMemory()
        }
        markTopicReadToLatest()
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

    fun loadForumMessages(before: String?, appendOlder: Boolean) {
        scope.launch {
            if (appendOlder) loadingOlder = true else loading = true
            if (!appendOlder) error = null
            teamsRepository.listForumMessages(teamId, topicId, before = before, limit = 50)
                .onSuccess { page ->
                    val visible = page.filter { m ->
                        m.deletedAt.isNullOrBlank() ||
                            m.deletedAt.equals("null", ignoreCase = true)
                    }
                    if (appendOlder) {
                        val existing = messages.map { it.id }.toSet()
                        val older = visible.filter { it.id !in existing }
                        messages.addAll(0, older)
                        trimForumMessagesInMemory()
                    } else {
                        messages.clear()
                        messages.addAll(visible)
                        trimForumMessagesInMemory()
                    }
                    hasMoreOlder = page.size >= 50
                }
                .onFailure { e ->
                    if (!appendOlder) error = e.toUserMessageRu(res)
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
        if (stableMessages.isNotEmpty()) {
            markTopicReadToLatest()
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
    LaunchedEffect(stableMessages.lastOrNull()?.id, listDerived.timeline.size, hasMoreOlder) {
        if (stableMessages.isEmpty()) return@LaunchedEffect
        val bottomIdx = listDerived.bottomLazyIndex(hasMoreOlder) ?: return@LaunchedEffect
        if (!initialScrollApplied) {
            initialScrollApplied = true
            runCatching { listState.scrollToItem(bottomIdx) }
            return@LaunchedEffect
        }
        if (isNearBottom) {
            runCatching { listState.animateScrollToItem(bottomIdx) }
        }
    }

    LaunchedEffect(typingHint) {
        val hint = typingHint ?: return@LaunchedEffect
        delay(4000)
        if (typingHint == hint) typingHint = null
    }

    DisposableEffect(teamId, topicId) {
        val onNew: (TeamForumMessageDto) -> Unit = { mergeNew(it) }
        val onEdited: (TeamForumMessageDto) -> Unit = { applyEdited(it) }
        val onDeleted: (TeamForumMessageDeletedEvent) -> Unit = { applyDeleted(it) }
        val onTyping: (TeamForumTypingEvent) -> Unit = { ev ->
            if (ev.userId != currentUserId) {
                typingHint = context.getString(R.string.team_forum_typing, ev.username)
            }
        }
        forumSocket.addMessageListener(onNew)
        forumSocket.addMessageEditedListener(onEdited)
        forumSocket.addMessageDeletedListener(onDeleted)
        forumSocket.addTypingListener(onTyping)
        forumSocket.connect(
            BuildConfig.API_BASE_URL,
            teamId,
            topicId,
        ) { tokenStore.getAccessToken() }
        onDispose {
            markTopicReadToLatest(forceSync = true)
            forumSocket.removeMessageListener(onNew)
            forumSocket.removeMessageEditedListener(onEdited)
            forumSocket.removeMessageDeletedListener(onDeleted)
            forumSocket.removeTypingListener(onTyping)
            forumSocket.connectTeamInbox(
                BuildConfig.API_BASE_URL,
                teamId,
            ) { tokenStore.getAccessToken() }
        }
    }

    CompositionLocalProvider(
        LocalOpenRemoteChatImagePreview provides openImages,
    ) {
    var composerBlockHeightPx by remember { mutableIntStateOf(0) }
    val composerReserveBottom = with(LocalDensity.current) {
        composerBlockHeightPx.toDp()
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = composerReserveBottom),
    ) {
        if (overlayUi) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.team_news_cd_back),
                    )
                }
                Text(
                    text = topicTitle.ifBlank { "…" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
            Modifier.weight(1f),
        ) {
            if (loading && messages.isEmpty()) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    strokeWidth = 2.dp,
                )
            } else {
                ForumTopicMessagesLazyList(
                    messages = stableMessages,
                    listDerived = listDerived,
                    listState = listState,
                    hasMoreOlder = hasMoreOlder,
                    loadingOlder = loadingOlder,
                    onLoadOlder = {
                        val oldestId = stableMessages.firstOrNull()?.id
                        if (oldestId != null) {
                            loadForumMessages(before = oldestId, appendOlder = true)
                        }
                    },
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
                        highlighted = highlightMessageId == msg.id,
                        onJumpToMessage = { targetId ->
                            val lazyIdx = listDerived.fullLazyIndexForMessageId(targetId, hasMoreOlder)
                            if (lazyIdx != null) {
                                scope.launch {
                                    runCatching { listState.animateScrollToItem(lazyIdx) }
                                    highlightMessageId = targetId
                                    delay(900)
                                    if (highlightMessageId == targetId) highlightMessageId = null
                                }
                            }
                        },
                        onToggleSelection = {
                            selectedMessageIds =
                                if (isSelected) selectedMessageIds - msg.id else selectedMessageIds + msg.id
                        },
                        onSwipeReply = {
                            if (msg.deletedAt.isNullOrBlank()) {
                                replyToMessage = msg
                            }
                        },
                        onOpenActions = {
                            if (msg.deletedAt.isNullOrBlank()) {
                                OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                activeActionMessageId = msg.id
                            }
                        },
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
                ChatScrollToLatestFab(
                    visible = showScrollToLatestFab,
                    newMessageCount = newMessagesWhileScrolledUp,
                    onClick = {
                        newMessagesWhileScrolledUp = 0
                        lastCountedNewestId = stableMessages.lastOrNull()?.id
                        scope.launch {
                            val bottomIdx = listDerived.bottomLazyIndex(hasMoreOlder) ?: return@launch
                            runCatching { listState.animateScrollToItem(bottomIdx) }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
        val forumReplyChat = remember(replyToMessage, teamId, topicId) {
            replyToMessage?.toDisplayChatMessage(teamId, topicId)
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { size -> composerBlockHeightPx = size.height }
                .chatComposerAppDock(),
        ) {
        ChatComposer(
            draft = draft,
            pickedImageUris = pickedImageUris,
            replyToMessage = forumReplyChat,
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
            onClearReply = { replyToMessage = null },
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
    }

    // legacy menu dialog removed (replaced with bottom sheet)

    editMessage?.let { msg ->
        OverlayModalScope(preparedByCaller = true) {
        OverlayAwareAlertDialog(
            onDismissRequest = { if (!editBusy) editMessage = null },
            title = { Text(stringResource(R.string.team_forum_edit_message_title)) },
            text = {
                OutlinedTextField(
                    value = editBody,
                    onValueChange = { editBody = it.take(4000) },
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editBusy,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !editBusy &&
                        (
                            editBody.trim().isNotEmpty() ||
                                msg.imageRelativeUrls.isNotEmpty() ||
                                !msg.imageRelativeUrl.isNullOrBlank()
                            ),
                    onClick = {
                        scope.launch {
                            editBusy = true
                            teamsRepository.patchForumMessage(teamId, topicId, msg.id, editBody)
                                .onSuccess {
                                    applyEdited(it)
                                    editMessage = null
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
                TextButton(onClick = { if (!editBusy) editMessage = null }) {
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
            OverlayModalScope(preparedByCaller = true) {
            ForumMessageActionsSheet(
                message = msg,
                canEdit = (msg.senderUserId == currentUserId || canModerateMessages) &&
                    msg.deletedAt.isNullOrBlank() &&
                    (
                        msg.text.isNotBlank() ||
                            msg.imageRelativeUrls.isNotEmpty() ||
                            !msg.imageRelativeUrl.isNullOrBlank()
                        ),
                canDelete = canDeleteForumMessage(msg),
                canForward = msg.deletedAt.isNullOrBlank(),
                onDismiss = { activeActionMessageId = null },
                onReply = {
                    replyToMessage = msg
                    activeActionMessageId = null
                },
                onEdit = {
                    editMessage = msg
                    editBody = msg.text
                    activeActionMessageId = null
                },
                onDelete = {
                    activeActionMessageId = null
                    scope.launch {
                        teamsRepository.deleteForumMessage(teamId, topicId, msg.id)
                            .onSuccess { removeMessage(msg.id) }
                            .onFailure { e -> error = e.toUserMessageRu(res) }
                    }
                },
                onForward = {
                    activeActionMessageId = null
                    scope.launch {
                        teamsRepository.forwardForumMessage(teamId, topicId, msg.id)
                            .onSuccess { mergeNew(it) }
                            .onFailure { e -> error = e.toUserMessageRu(res) }
                    }
                },
            )
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
                                .onSuccess { ids.forEach { removeMessage(it) } }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumMessageActionsSheet(
    message: TeamForumMessageDto,
    canEdit: Boolean,
    canDelete: Boolean,
    canForward: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
) {
    val context = LocalContext.current
    val stickerStem = remember(message.text) { StickerPacks.stemForMessage(message.text) }
    val canCopy = forumMessageHasCopyableContent(message)
    OverlayAwareBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                    vertical = SquadRelayDimens.itemGap,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MessageSheetPreviewSurface {
                Text(
                    text = message.senderUsername.trim().ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                when {
                    stickerStem != null -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(StickerPacks.assetUriForMessage(message.text))
                                    .size(200)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(R.string.cd_chat_sticker),
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Text(
                                text = replyPreviewText(message.text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    message.text.isNotBlank() -> {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    message.imageRelativeUrls.isNotEmpty() ||
                        !message.imageRelativeUrl.isNullOrBlank() -> {
                        Text(
                            text = stringResource(R.string.chat_copy_image_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.chat_sheet_preview_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            MessageSheetDividerSpaced()
            MessageSheetActionRow(
                icon = Icons.Outlined.ContentCopy,
                label = stringResource(R.string.chat_action_copy),
                onClick = {
                    copyForumMessageToClipboard(context, message)
                    onDismiss()
                },
                enabled = canCopy,
            )
            MessageSheetActionRow(
                icon = Icons.AutoMirrored.Outlined.Reply,
                label = stringResource(R.string.chat_action_reply),
                onClick = onReply,
            )
            MessageSheetActionRow(
                icon = Icons.Outlined.ContentPaste,
                label = stringResource(R.string.chat_action_forward),
                onClick = onForward,
                enabled = canForward,
            )
            MessageSheetActionRow(
                icon = Icons.Outlined.Edit,
                label = stringResource(R.string.chat_action_edit),
                onClick = onEdit,
                enabled = canEdit,
            )
            MessageSheetActionRow(
                icon = Icons.Outlined.DeleteOutline,
                label = stringResource(R.string.chat_action_delete),
                onClick = onDelete,
                enabled = canDelete,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

private enum class ForumTopicListFilter {
    All,
    Unread,
    Recent,
}

