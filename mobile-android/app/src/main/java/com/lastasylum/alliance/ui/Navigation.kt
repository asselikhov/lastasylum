package com.lastasylum.alliance.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.BorderStroke
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatRoomKindResolver
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.components.AtmosphericBackground
import com.lastasylum.alliance.ui.components.premium.PremiumGlassBar
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.theme.premium.PremiumMotion
import android.content.Intent
import com.lastasylum.alliance.MainActivity
import com.lastasylum.alliance.di.ChatViewModelRegistry
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayMainAppPresencePolicy
import com.lastasylum.alliance.push.FcmTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.lastasylum.alliance.ui.admin.AdminViewModel
import com.lastasylum.alliance.ui.admin.AdminViewModelFactory
import com.lastasylum.alliance.ui.chat.ChatViewModel
import com.lastasylum.alliance.ui.chat.ChatViewModelFactory
import com.lastasylum.alliance.ui.screens.AdminScreen
import com.lastasylum.alliance.ui.screens.ChatScreen
import com.lastasylum.alliance.ui.screens.OverlayControlScreen
import com.lastasylum.alliance.ui.screens.ProfileScreen
import com.lastasylum.alliance.ui.screens.TeamScreen

enum class AppTab(val route: String, val titleRes: Int) {
    CHAT("chat", R.string.tab_chat),
    TEAM("team", R.string.tab_team),
    OVERLAY("overlay", R.string.tab_overlay),
    PROFILE("profile", R.string.tab_profile),
    ADMIN("admin", R.string.tab_admin),
}

@Composable
fun AppNavigation(
    userId: String,
    username: String,
    role: String,
    overlayTabVisible: Boolean,
    onLogout: () -> Unit,
    chatViewModelFactory: ChatViewModelFactory,
    adminViewModelFactory: AdminViewModelFactory,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as ComponentActivity
    val app = remember(activity) { AppContainer.from(activity.applicationContext) }
    val chatViewModel: ChatViewModel = viewModel(
        viewModelStoreOwner = activity,
        key = "alliance_chat",
        factory = chatViewModelFactory,
    )
    val adminViewModel: AdminViewModel = viewModel(
        viewModelStoreOwner = activity,
        key = "alliance_admin",
        factory = adminViewModelFactory,
    )
    LaunchedEffect(Unit) {
        if (ChatSessionCache.getFreshRooms() != null) return@LaunchedEffect
        val s = chatViewModel.state.value
        if (s.rooms.isEmpty() && !s.isRoomsLoading) {
            chatViewModel.refreshChat()
        }
    }

    DisposableEffect(chatViewModel) {
        ChatViewModelRegistry.bind(chatViewModel)
        CombatOverlayService.bindActivityChatViewModel(chatViewModel)
        onDispose {
            if (ChatViewModelRegistry.shared === chatViewModel) {
                ChatViewModelRegistry.bind(null)
            }
            if (CombatOverlayService.activityScopedChatViewModel === chatViewModel) {
                CombatOverlayService.bindActivityChatViewModel(null)
            }
        }
    }

    LaunchedEffect(userId) {
        if (userId.isBlank()) return@LaunchedEffect
        CombatOverlayService.ensureRuntimeIfUserEnabled(activity.applicationContext)
    }

    // FGS держим в фоне, пока включена панель; HUD рисуется только когда гейт видит игру на экране.
    DisposableEffect(activity, userId) {
        val appContext = activity.applicationContext
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    chatViewModel.setAppInForeground(true)
                    CombatOverlayService.setMainAppUiInForeground(true)
                    CombatOverlayService.requestGateRecheckIfRunning(appContext)
                    if (userId.isNotBlank()) {
                        CombatOverlayService.ensureRuntimeIfUserEnabled(appContext)
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            runCatching { FcmTokenManager.registerWithBackend(appContext) }
                            delay(OverlayMainAppPresencePolicy.AWAY_PING_DEFER_MS)
                            val skipAway = OverlayMainAppPresencePolicy.shouldSkipAwayPing(
                                inGameOverlayUiActive = CombatOverlayService.inGameOverlayUiActive.value,
                                targetGameForeground = CombatOverlayService.isTargetGameForeground(),
                            )
                            if (!skipAway) {
                                runCatching {
                                    app.usersRepository.updatePresence("away")
                                }
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    chatViewModel.setAppInForeground(false)
                    CombatOverlayService.setMainAppUiInForeground(false)
                    if (userId.isNotBlank()) {
                        CombatOverlayService.ensureRuntimeIfUserEnabled(appContext)
                    }
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    val inGameOverlayUiActive by CombatOverlayService.inGameOverlayUiActive.collectAsStateWithLifecycle()

    LaunchedEffect(userId, inGameOverlayUiActive) {
        if (userId.isBlank()) return@LaunchedEffect
        while (isActive) {
            delay(45_000)
            // Пока гейт видит игру — ingame heartbeat в FGS; иначе «online» для push раскопок.
            if (OverlayMainAppPresencePolicy.shouldSkipOnlinePing(
                    inGameOverlayUiActive = inGameOverlayUiActive,
                    targetGameForeground = CombatOverlayService.isTargetGameForeground(),
                )
            ) {
                continue
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    app.usersRepository.updatePresence("online")
                }
            }
        }
    }

    var currentTabRoute by rememberSaveable { mutableStateOf(AppTab.TEAM.route) }
    var retainedTabRoutes by remember { mutableStateOf(setOf(AppTab.TEAM.route)) }

    fun selectTab(route: String) {
        if (route !in retainedTabRoutes) {
            retainedTabRoutes = retainedTabRoutes + route
        }
        currentTabRoute = route
    }

    val visibleTabs = remember(role, overlayTabVisible) {
        AppTab.entries.filter { tab ->
            when (tab) {
                AppTab.ADMIN -> com.lastasylum.alliance.data.auth.AccountRoles.isAppAdmin(role)
                AppTab.OVERLAY -> overlayTabVisible
                else -> true
            }
        }
    }

    LaunchedEffect(activity.intent, visibleTabs) {
        val tab = activity.intent?.getStringExtra(MainActivity.EXTRA_START_TAB)?.trim().orEmpty()
        if (tab.isEmpty()) return@LaunchedEffect
        activity.intent = Intent(activity.intent).apply {
            removeExtra(MainActivity.EXTRA_START_TAB)
        }
        val route = when (tab) {
            AppTab.CHAT.route, "chat" -> AppTab.CHAT.route
            AppTab.TEAM.route, "team", "news" -> AppTab.TEAM.route
            AppTab.OVERLAY.route, "overlay" -> AppTab.OVERLAY.route
            else -> null
        } ?: return@LaunchedEffect
        if (visibleTabs.none { it.route == route }) return@LaunchedEffect
        selectTab(route)
    }

    LaunchedEffect(overlayTabVisible) {
        if (!overlayTabVisible && currentTabRoute == AppTab.OVERLAY.route) {
            selectTab(AppTab.CHAT.route)
        }
    }

    val msgApproved = stringResource(R.string.admin_ok_approved)
    val msgRemoved = stringResource(R.string.admin_ok_removed)
    val msgPending = stringResource(R.string.admin_ok_pending)
    val msgRole = stringResource(R.string.admin_ok_role)
    val msgNicknameSaved = stringResource(R.string.admin_players_save_game_ok)
    val msgTeamSaved = stringResource(R.string.admin_team_edit_saved)
    val msgDeleted = stringResource(R.string.admin_ok_deleted)
    val msgOverlaySaved = stringResource(R.string.admin_ok_overlay)
    val msgStickerSaved = stringResource(R.string.admin_sticker_saved)
    val chatTabUnreadTotal by chatViewModel.state
        .map { it.tabUnreadBadge }
        .distinctUntilChanged()
        .collectAsStateWithLifecycle(0)
    val chatRouteActive = currentTabRoute == AppTab.CHAT.route
    DisposableEffect(chatRouteActive) {
        if (chatRouteActive) {
            chatViewModel.onChatTabResumed()
        } else {
            chatViewModel.onChatTabPaused()
        }
        onDispose { chatViewModel.onChatTabPaused() }
    }
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // IME не сжимает Scaffold (adjustNothing): навбар остаётся внизу, системная клавиатура перекрывает его.
        // Подъём полей — imePadding на композерах/формах, не на bottomBar.
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Фиксированный отступ: inset navigationBars меняется при IME и дёргает панель.
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = SquadRelayDimens.bottomBarOuterPadding,
                    ),
            ) {
                PremiumGlassBar(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(SquadRelayDimens.bottomNavigationBarCornerRadius),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        visibleTabs.forEach { tab ->
                            val isSelected = currentTabRoute == tab.route
                            val tint = if (isSelected) {
                                PremiumColors.accentCyan
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val label = stringResource(tab.titleRes)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(SquadRelayDimens.bottomNavigationBarCornerRadius))
                                    .background(
                                        if (isSelected) {
                                            PremiumColors.accentPurple.copy(alpha = 0.28f)
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .clickable { selectTab(tab.route) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    when (tab) {
                                        AppTab.CHAT -> BadgedBox(
                                            badge = {
                                                if (chatTabUnreadTotal > 0) {
                                                    Badge {
                                                        Text(
                                                            text = if (chatTabUnreadTotal >= 99) {
                                                                "99+"
                                                            } else {
                                                                chatTabUnreadTotal.toString()
                                                            },
                                                        )
                                                    }
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                                contentDescription = label,
                                                tint = tint,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }

                                        AppTab.OVERLAY -> Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = label,
                                            tint = tint,
                                            modifier = Modifier.size(22.dp),
                                        )

                                        AppTab.PROFILE -> Icon(
                                            imageVector = Icons.Outlined.PersonOutline,
                                            contentDescription = label,
                                            tint = tint,
                                            modifier = Modifier.size(22.dp),
                                        )

                                        AppTab.ADMIN -> Icon(
                                            imageVector = Icons.Outlined.AdminPanelSettings,
                                            contentDescription = label,
                                            tint = tint,
                                            modifier = Modifier.size(22.dp),
                                        )

                                        AppTab.TEAM -> Icon(
                                            imageVector = Icons.Outlined.Groups,
                                            contentDescription = label,
                                            tint = tint,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            lineHeight = 12.sp,
                                        ),
                                        color = tint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .background(PremiumColors.voidMid),
        ) {
            val tabsToCompose = retainedTabRoutes.intersect(visibleTabs.map { it.route }.toSet())
            tabsToCompose.forEach { route ->
                val active = route == currentTabRoute
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .mainTabSlot(active),
                ) {
                    CompositionLocalProvider(LocalMainTabActive provides active) {
                    when (route) {
                        AppTab.CHAT.route -> {
                if (active) {
                LaunchedEffect(Unit) {
                    chatViewModel.refreshTeamProfileGateLight()
                }
                DisposableEffect(app.chatRepository) {
                    val listener = { chatViewModel.onOverlayChatPanelClosed() }
                    app.chatRepository.addOverlayChatPanelClosedListener(listener)
                    onDispose {
                        app.chatRepository.removeOverlayChatPanelClosedListener(listener)
                    }
                }
                val listPane by chatViewModel.listPaneState.collectAsStateWithLifecycle()
                val chromePane by chatViewModel.chromePaneState.collectAsStateWithLifecycle()
                val composerPane by chatViewModel.composerPaneState.collectAsStateWithLifecycle()
                val listDerived by chatViewModel.listDerived.collectAsStateWithLifecycle()
                val draftMessage by chatViewModel.draftMessage.collectAsStateWithLifecycle()
                val pickedImageUris by chatViewModel.pickedImageUris.collectAsStateWithLifecycle()
                val typingPeers by chatViewModel.typingPeers.collectAsStateWithLifecycle()
                val otherReadUptoMessageId by chatViewModel.otherReadUptoMessageId.collectAsStateWithLifecycle()
                ChatScreen(
                    listPane = listPane,
                    chromePane = chromePane,
                    composerPane = composerPane,
                    listDerived = listDerived,
                    typingPeers = typingPeers,
                    draftMessage = draftMessage,
                    pickedImageUris = pickedImageUris,
                    otherReadUptoMessageId = otherReadUptoMessageId,
                    onSelectRoom = chatViewModel::selectRoom,
                    onClearError = chatViewModel::clearError,
                    onLoadOlder = chatViewModel::loadOlderMessages,
                    onDraftChange = chatViewModel::setDraftMessage,
                    onSendDraft = chatViewModel::sendDraftMessage,
                    onSendStickerPayload = { body -> chatViewModel.sendMessage(body) },
                    onPickImages = { uris, append ->
                        chatViewModel.onImagesPicked(uris, append)
                    },
                    onRemovePickedImage = chatViewModel::removePickedImage,
                    onClearPickedImages = chatViewModel::clearPickedImages,
                    onReplyToMessage = chatViewModel::beginReplyToMessage,
                    onClearReply = chatViewModel::clearReplyToMessage,
                    onOpenMessageActions = chatViewModel::openMessageActions,
                    onDismissMessageActions = chatViewModel::dismissMessageActions,
                    onRequestDeleteMessage = chatViewModel::requestDeleteMessage,
                    onDismissDeleteMessage = chatViewModel::dismissDeleteMessage,
                    onConfirmDeleteMessage = chatViewModel::confirmDeleteMessage,
                    onBeginMessageSelection = chatViewModel::beginMessageSelection,
                    onToggleMessageSelection = chatViewModel::toggleMessageSelection,
                    onClearMessageSelection = chatViewModel::clearMessageSelection,
                    onRequestBulkDelete = chatViewModel::requestBulkDelete,
                    onDismissBulkDeleteConfirm = chatViewModel::dismissBulkDeleteConfirm,
                    onConfirmDeleteSelectedMessages = chatViewModel::confirmDeleteSelectedMessages,
                    onRetrySendFailure = chatViewModel::retrySendFailure,
                    onDismissSendFailure = chatViewModel::dismissSendFailure,
                    onBeginEditMessage = chatViewModel::beginEditMessage,
                    onClearEditMessage = chatViewModel::cancelEditMessage,
                    onToggleReaction = chatViewModel::toggleReaction,
                    onScrollToLatest = chatViewModel::scrollToLatestMessages,
                    onJumpToQuotedMessage = chatViewModel::jumpToQuotedMessage,
                    onConsumeScrollToMessage = chatViewModel::consumeScrollToMessage,
                    onClearHighlightMessage = chatViewModel::clearHighlightMessage,
                    onConsumeTransientNotice = chatViewModel::consumeTransientNotice,
                    onMessageListScrollInProgress = chatViewModel::setMessageListScrollInProgress,
                    messageListKey = chatViewModel::messageListCompositionKey,
                    onClearRoomHistory = chatViewModel::clearHistoryForSelectedRoom,
                    onMarkAllRoomsRead = chatViewModel::markAllRoomsReadUpToLatest,
                    onPinMessage = chatViewModel::pinMessage,
                    onUnpinRoom = chatViewModel::unpinSelectedRoom,
                    onPinnedBarTap = chatViewModel::onPinnedBarTap,
                    onJumpToPinnedMessage = chatViewModel::onJumpToPinnedMessage,
                    onUnpinOnePinned = chatViewModel::unpinOnePinnedMessage,
                    onDismissPinBar = chatViewModel::dismissPinBarForRoom,
                    onRestorePinBar = chatViewModel::restorePinBarForRoom,
                )
                }
                        }
                        AppTab.OVERLAY.route -> {
                            if (active && overlayTabVisible) {
                                OverlayControlScreen()
                            }
                        }
                        AppTab.PROFILE.route -> {
                            if (active) {
                            ProfileScreen(
                                username = username,
                                onLogout = onLogout,
                            )
                            }
                        }
                        AppTab.TEAM.route -> {
                            if (active) {
                            TeamScreen(
                                currentUserId = userId,
                                teamsRepository = app.teamsRepository,
                                usersRepository = app.usersRepository,
                            )
                            }
                        }
                        AppTab.ADMIN.route -> {
                            if (active) {
                            val adminState by adminViewModel.state.collectAsStateWithLifecycle()
                            AdminScreen(
                    currentUserId = userId,
                    state = adminState,
                    onNavigateBack = adminViewModel::navigateBack,
                    onOpenRoute = adminViewModel::openRoute,
                    onOpenPlayerTeam = adminViewModel::openPlayerTeam,
                    onTeamSearchChange = adminViewModel::setTeamSearchQuery,
                    onPlayersSearchChange = adminViewModel::setPlayersSearch,
                    onPlayersSegmentChange = adminViewModel::setPlayersSegment,
                    onPlayersServerFilter = adminViewModel::setPlayersServerFilter,
                    onTeamsServerFilter = adminViewModel::setTeamsServerFilter,
                    onTeamDetailTabChange = adminViewModel::setTeamDetailTab,
                    onOpenChatRoom = adminViewModel::openChatRoomViewer,
                    onOpenForumTopic = adminViewModel::openForumTopicViewer,
                    onRefreshOverview = adminViewModel::refreshOverview,
                    onRefreshPlayerTeams = adminViewModel::refreshPlayerTeams,
                    onRefreshTeamDetail = adminViewModel::refreshTeamDetail,
                    onRefreshPlayers = adminViewModel::refreshPlayersScreen,
                    onRefreshAlliances = adminViewModel::refreshAlliances,
                    onAllianceOverlayChange = { publicId, enabled ->
                        adminViewModel.setAllianceOverlay(publicId, enabled, msgOverlaySaved)
                    },
                    onOpenStickerSettings = adminViewModel::openStickerSettings,
                    onCloseStickerSettings = adminViewModel::closeStickerSettings,
                    onSelectStickerPack = adminViewModel::setStickerSelectedPack,
                    onStickerMemberSearchChange = adminViewModel::setStickerMemberSearch,
                    onToggleStickerAllianceRole = adminViewModel::toggleStickerAllianceRole,
                    onToggleStickerUserGrant = adminViewModel::toggleStickerUserGrant,
                    onSaveStickerAccess = { adminViewModel.saveStickerAccess(msgStickerSaved) },
                    onClearStickerAccessError = adminViewModel::clearStickerAccessError,
                    onOpenPlayerStickerEditor = adminViewModel::openPlayerStickerEditor,
                    onTogglePlayerStickerPack = adminViewModel::togglePlayerStickerPack,
                    onSavePlayerStickerAccess = {
                        adminViewModel.savePlayerStickerAccess(msgStickerSaved)
                    },
                    onApprove = { id -> adminViewModel.setMembership(id, "active", msgApproved) },
                    onRemoveFromTeam = { id -> adminViewModel.setMembership(id, "removed", msgRemoved) },
                    onRestorePending = { id -> adminViewModel.setMembership(id, "pending", msgPending) },
                    onSetRole = { memberId, newRole -> adminViewModel.setRole(memberId, newRole, msgRole) },
                    onDeleteUser = { memberId -> adminViewModel.deleteUser(memberId, msgDeleted) },
                    onClearActionError = adminViewModel::clearActionError,
                    onDismissSnack = adminViewModel::clearSnack,
                    onUpdateGameIdentity = { userId, identityId, nickname, server ->
                        adminViewModel.updateGameIdentityAdmin(
                            userId,
                            identityId,
                            nickname,
                            server,
                            msgNicknameSaved,
                        )
                    },
                    onUpdatePlayerTeam = { teamId, tag, name ->
                        adminViewModel.updatePlayerTeamBranding(
                            teamId,
                            name,
                            tag,
                            msgTeamSaved,
                        )
                    },
                    onLoadMorePlayers = adminViewModel::loadMorePlayersList,
                    onLoadMorePlayerTeams = adminViewModel::loadMorePlayerTeams,
                    onRequestClearAllChatHistory = adminViewModel::requestClearAllChatHistoryConfirm,
                    onDismissClearAllChatHistoryConfirm = adminViewModel::dismissClearAllChatHistoryConfirm,
                    onConfirmClearAllChatHistory = adminViewModel::confirmClearAllChatHistory,
                            )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

