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
import com.lastasylum.alliance.ui.chat.capForumMessagesTrimNewestOnly
import com.lastasylum.alliance.ui.chat.mergeForumMessagesPage
import com.lastasylum.alliance.ui.chat.mergePreservingForumMedia
import com.lastasylum.alliance.ui.chat.ACTIVE_FORUM_RECONCILE_INTERVAL_MS
import com.lastasylum.alliance.ui.chat.buildOptimisticForumMessage
import com.lastasylum.alliance.ui.chat.removePendingForumOutgoing
import com.lastasylum.alliance.ui.chat.replaceMatchingPendingForumOutgoing
import com.lastasylum.alliance.ui.chat.shouldBlockOwnForumOutgoingRealtime
import com.lastasylum.alliance.ui.chat.shouldTriggerGapReconcile
import com.lastasylum.alliance.data.teams.ForumMessageStash
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
import androidx.compose.runtime.mutableLongStateOf
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
import com.lastasylum.alliance.ui.teamforum.ForumListViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Application
import com.lastasylum.alliance.data.chat.ChatReaction
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.ForumSocketIngress
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent
import com.lastasylum.alliance.data.teams.TeamForumTopicReadEvent
import com.lastasylum.alliance.ui.chat.ForumPinCoordinator
import com.lastasylum.alliance.ui.chat.PinnedMessageBar
import com.lastasylum.alliance.ui.chat.PinnedMessagesCompactChip
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
import com.lastasylum.alliance.ui.util.formatForumTopicListTimeRu
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
import java.time.Instant
import java.util.UUID
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
import com.lastasylum.alliance.ui.chat.ChatStickerFormat
import android.content.ContentResolver
import android.os.ParcelFileDescriptor
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
    onForumTopicsSynced: (List<TeamForumTopicDto>, Map<String, Int>) -> Unit = { _, _ -> },
    onForumInboxChanged: () -> Unit = {},
    onRegisterMarkReadAction: ((() -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val overlayUi = LocalOverlayUiMode.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val topicTitles = remember { mutableStateMapOf<String, String>() }
    val topicSnapshots = remember { mutableStateMapOf<String, TeamForumTopicDto>() }
    var listRefreshNonce by remember { mutableIntStateOf(0) }
    var topicActivityPatch by remember {
        mutableStateOf<com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent?>(null)
    }
    var topicPinPatch by remember { mutableStateOf<TeamForumTopicPinChangedEvent?>(null) }
    var markReadHandlers by remember { mutableStateOf<Map<String, () -> Unit>>(emptyMap()) }
    var overlayTopicFlush by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
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
    LaunchedEffect(overlayUi, overlayTopicFlush, navBackStackEntry?.destination?.route) {
        if (!overlayUi) {
            com.lastasylum.alliance.overlay.CombatOverlayService.registerOverlayForumFlushPendingRead(null)
            return@LaunchedEffect
        }
        val route = navBackStackEntry?.destination?.route
        val onTopic = route != null && route.startsWith("forum_topic/")
        com.lastasylum.alliance.overlay.CombatOverlayService.registerOverlayForumFlushPendingRead(
            if (onTopic) {
                { overlayTopicFlush?.invoke() }
            } else {
                null
            },
        )
    }
    LaunchedEffect(teamId, currentUserId) {
        val app = AppContainer.from(context.applicationContext)
        ForumMessageStash.setOverflowListener { overflowTeamId, overflowTopicId ->
            if (overflowTeamId.trim() != teamId.trim()) return@setOverflowListener
            val uid = currentUserId.trim()
            if (uid.isEmpty()) return@setOverflowListener
            scope.launch {
                app.forumRepository.syncMessages(uid, overflowTeamId, overflowTopicId, force = true)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { ForumMessageStash.setOverflowListener(null) }
    }
    LaunchedEffect(listRefreshNonce) {
        if (listRefreshNonce > 0) onForumInboxChanged()
    }
    DisposableEffect(overlayUi) {
        if (overlayUi) {
            val bumpListRefresh: () -> Unit = { listRefreshNonce++ }
            com.lastasylum.alliance.overlay.CombatOverlayService.registerOverlayForumRehydrateAction(
                bumpListRefresh,
            )
            onDispose {
                com.lastasylum.alliance.overlay.CombatOverlayService.registerOverlayForumRehydrateAction(null)
            }
        } else {
            onDispose { }
        }
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
            val app = AppContainer.from(context.applicationContext)
            val onForumMessage: (TeamForumMessageDto) -> Unit = { message ->
                val uid = currentUserId.trim()
                if (uid.isNotEmpty() &&
                    ForumSocketIngress.claimForPersistence(message.topicId, message.id)
                ) {
                    scope.launch(Dispatchers.IO) {
                        app.forumRepository.onForumSocketMessage(
                            userId = uid,
                            teamId = teamId,
                            topicId = message.topicId,
                            message = message,
                        )
                    }
                }
            }
            forumSocket.addMessageListener(onForumMessage)
            forumSocket.addTopicActivityListener(onTopicActivity)
            forumSocket.addTopicPinChangedListener(onTopicPin)
            forumSocket.connectTeamInbox(
                com.lastasylum.alliance.BuildConfig.API_BASE_URL,
                teamId,
            ) { tokenStore.getAccessToken() }
            onDispose {
                forumSocket.removeMessageListener(onForumMessage)
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
            TeamForumListScreen(
                teamId = teamId,
                currentUserId = currentUserId,
                canManageTopics = canManageTopics,
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
            val app = AppContainer.from(context.applicationContext)
            TeamForumTopicScreen(
                teamId = teamId,
                topicId = topicId,
                topicTitle = title,
                topicSnapshot = topicSnapshot,
                currentUserId = currentUserId,
                canModerateMessages = canModerateForumMessages,
                forumRepository = app.forumRepository,
                forumSocket = forumSocket,
                tokenStore = tokenStore,
                sectionActive = sectionActive,
                enabledStickerPackKeys = enabledStickerPackKeys,
                onBack = {
                    nav.popBackStack()
                },
                onInboxChanged = onForumInboxChanged,
                onProvideMarkReadAction = registerMarkReadAction,
                onRegisterOverlayFlush = { overlayTopicFlush = it },
                onTopicSnapshotUpdate = { topicSnapshots[topicId] = it },
            )
        }
    }
}
