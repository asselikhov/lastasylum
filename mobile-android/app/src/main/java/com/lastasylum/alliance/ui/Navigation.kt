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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.BorderStroke
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatRoomKindResolver
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.components.AtmosphericBackground
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import android.content.Intent
import com.lastasylum.alliance.MainActivity
import com.lastasylum.alliance.di.ChatViewModelRegistry
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.push.FcmTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                    CombatOverlayService.requestGateRecheckIfRunning(appContext)
                    if (userId.isNotBlank()) {
                        CombatOverlayService.ensureRuntimeIfUserEnabled(appContext)
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            runCatching { FcmTokenManager.registerWithBackend(appContext) }
                            if (!CombatOverlayService.inGameOverlayUiActive.value) {
                                runCatching {
                                    app.usersRepository.updatePresence("away")
                                }
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
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
            if (inGameOverlayUiActive) continue
            runCatching {
                withContext(Dispatchers.IO) {
                    app.usersRepository.updatePresence("online")
                }
            }
        }
    }

    val tabMotionDisabled = rememberTabMotionDisabled()
    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry.value?.destination

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
            AppTab.OVERLAY.route, "overlay" -> AppTab.OVERLAY.route
            else -> null
        } ?: return@LaunchedEffect
        if (visibleTabs.none { it.route == route }) return@LaunchedEffect
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
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
    val chatState by chatViewModel.state.collectAsStateWithLifecycle()
    val chatTabUnreadTotal = chatState.tabUnreadBadge
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // IME: MainActivity adjustResize shrinks content above keyboard; exclude IME from Scaffold padding to avoid double insets.
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = SquadRelaySurfaces.barColor(),
                    tonalElevation = 0.dp,
                    shadowElevation = 4.dp,
                    border = SquadRelaySurfaces.panelBorder(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        visibleTabs.forEach { tab ->
                            val isSelected =
                                currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            val tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val label = stringResource(tab.titleRes)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .clickable {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
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
                .fillMaxSize(),
        ) {
            AtmosphericBackground(Modifier.fillMaxSize())
            NavHost(
            navController = navController,
            startDestination = AppTab.CHAT.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (tabMotionDisabled) EnterTransition.None
                else fadeIn(tween(180)) + slideInHorizontally(tween(180)) { w -> w / 10 }
            },
            exitTransition = {
                if (tabMotionDisabled) ExitTransition.None
                else fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { w -> -w / 12 }
            },
            popEnterTransition = {
                if (tabMotionDisabled) EnterTransition.None
                else fadeIn(tween(180)) + slideInHorizontally(tween(180)) { w -> -w / 10 }
            },
            popExitTransition = {
                if (tabMotionDisabled) ExitTransition.None
                else fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { w -> w / 12 }
            },
        ) {
            composable(AppTab.CHAT.route) {
                LaunchedEffect(Unit) {
                    chatViewModel.refreshTeamProfileGate()
                }
                DisposableEffect(Unit) {
                    chatViewModel.onChatTabResumed()
                    onDispose { chatViewModel.onChatTabPaused() }
                }
                DisposableEffect(app.chatRepository) {
                    val listener = { chatViewModel.onOverlayChatPanelClosed() }
                    app.chatRepository.addOverlayChatPanelClosedListener(listener)
                    onDispose {
                        app.chatRepository.removeOverlayChatPanelClosedListener(listener)
                    }
                }
                val draftMessage by chatViewModel.draftMessage.collectAsStateWithLifecycle()
                val pickedImageUris by chatViewModel.pickedImageUris.collectAsStateWithLifecycle()
                val typingPeers by chatViewModel.typingPeers.collectAsStateWithLifecycle()
                val otherReadUptoMessageId by chatViewModel.otherReadUptoMessageId.collectAsStateWithLifecycle()
                ChatScreen(
                    state = chatState,
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
                    onEditMessage = chatViewModel::editMessage,
                    onForwardMessage = chatViewModel::forwardMessage,
                    onToggleReaction = chatViewModel::toggleReaction,
                    onScrollToLatest = chatViewModel::scrollToLatestMessages,
                    onJumpToQuotedMessage = chatViewModel::jumpToQuotedMessage,
                    onConsumeScrollToMessage = chatViewModel::consumeScrollToMessage,
                    onClearHighlightMessage = chatViewModel::clearHighlightMessage,
                    onConsumeTransientNotice = chatViewModel::consumeTransientNotice,
                )
            }
            composable(AppTab.OVERLAY.route) {
                LaunchedEffect(overlayTabVisible) {
                    if (!overlayTabVisible) {
                        navController.navigate(AppTab.CHAT.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                        }
                    }
                }
                if (overlayTabVisible) {
                    OverlayControlScreen()
                }
            }
            composable(AppTab.PROFILE.route) {
                ProfileScreen(
                    username = username,
                    onLogout = onLogout,
                )
            }
            composable(AppTab.TEAM.route) {
                TeamScreen(
                    currentUserId = userId,
                    teamsRepository = app.teamsRepository,
                    usersRepository = app.usersRepository,
                )
            }
            composable(AppTab.ADMIN.route) {
                val adminViewModel: AdminViewModel = viewModel(factory = adminViewModelFactory)
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
                    onToggleStickerAllianceRole = adminViewModel::toggleStickerAllianceRole,
                    onSaveStickerAccess = { adminViewModel.saveStickerAccess(msgStickerSaved) },
                    onClearStickerAccessError = adminViewModel::clearStickerAccessError,
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
                )
            }
        }
        }
    }
}

@Composable
private fun rememberTabMotionDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
