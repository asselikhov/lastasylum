package com.lastasylum.alliance.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        chatViewModel.refreshChat()
    }

    LaunchedEffect(userId) {
        if (userId.isBlank()) return@LaunchedEffect
        while (isActive) {
            delay(45_000)
            runCatching {
                withContext(Dispatchers.IO) {
                    app.usersRepository.updatePresence("online")
                }
            }
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry.value?.destination

    val visibleTabs = remember(role, overlayTabVisible) {
        AppTab.entries.filter { tab ->
            when (tab) {
                AppTab.ADMIN -> role == "R5"
                AppTab.OVERLAY -> overlayTabVisible
                else -> true
            }
        }
    }

    val msgApproved = stringResource(R.string.admin_ok_approved)
    val msgRemoved = stringResource(R.string.admin_ok_removed)
    val msgPending = stringResource(R.string.admin_ok_pending)
    val msgRole = stringResource(R.string.admin_ok_role)
    val msgRename = stringResource(R.string.admin_ok_rename)
    val msgDeleted = stringResource(R.string.admin_ok_deleted)
    val msgRoomCreated = stringResource(R.string.admin_ok_room_created)
    val msgRoomRenamed = stringResource(R.string.admin_ok_room_renamed)
    val msgRoomDeleted = stringResource(R.string.admin_ok_room_deleted)
    val msgOverlaySaved = stringResource(R.string.admin_ok_overlay)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // adjustNothing + imePadding on chat composer. Exclude IME from Scaffold content insets.
        // While IME is visible, hide the tab bar so it does not sit between composer and keyboard
        // (same idea as Telegram: typing uses full width above the keyboard).
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        bottomBar = {
            val density = LocalDensity.current
            val imeInsets = WindowInsets.ime
            // Boolean only — avoids recomposing the whole bottom bar on every IME animation frame.
            val keyboardOpen by remember(imeInsets, density) {
                derivedStateOf { imeInsets.getBottom(density) > 0 }
            }
            if (!keyboardOpen) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        visibleTabs.forEach { tab ->
                            val isSelected =
                                currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            IconButton(
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                when (tab) {
                                    AppTab.CHAT -> Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = stringResource(tab.titleRes),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )

                                    AppTab.OVERLAY -> Icon(
                                        imageVector = Icons.Outlined.RadioButtonChecked,
                                        contentDescription = stringResource(tab.titleRes),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )

                                    AppTab.PROFILE -> Icon(
                                        imageVector = Icons.Outlined.PersonOutline,
                                        contentDescription = stringResource(tab.titleRes),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )

                                    AppTab.ADMIN -> Icon(
                                        imageVector = Icons.Outlined.AdminPanelSettings,
                                        contentDescription = stringResource(tab.titleRes),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )

                                    AppTab.TEAM -> Icon(
                                        imageVector = Icons.Outlined.Groups,
                                        contentDescription = stringResource(tab.titleRes),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppTab.CHAT.route,
            modifier = Modifier.padding(contentPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(AppTab.CHAT.route) {
                LaunchedEffect(Unit) {
                    chatViewModel.refreshTeamProfileGate()
                }
                val chatState by chatViewModel.state.collectAsStateWithLifecycle()
                val draftMessage by chatViewModel.draftMessage.collectAsStateWithLifecycle()
                val pickedImageUris by chatViewModel.pickedImageUris.collectAsStateWithLifecycle()
                val typingPeers by chatViewModel.typingPeers.collectAsStateWithLifecycle()
                ChatScreen(
                    state = chatState,
                    typingPeers = typingPeers,
                    draftMessage = draftMessage,
                    pickedImageUris = pickedImageUris,
                    onSelectRoom = chatViewModel::selectRoom,
                    onClearError = chatViewModel::clearError,
                    onLoadOlder = chatViewModel::loadOlderMessages,
                    onDraftChange = chatViewModel::setDraftMessage,
                    onSendDraft = chatViewModel::sendDraftMessage,
                    onSendStickerPayload = { body -> chatViewModel.sendMessage(body) },
                    onPickImages = chatViewModel::onImagesPicked,
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
                    OverlayControlScreen(role = role)
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
                )
            }
            composable(AppTab.ADMIN.route) {
                val adminViewModel: AdminViewModel = viewModel(factory = adminViewModelFactory)
                val adminState by adminViewModel.state.collectAsStateWithLifecycle()
                AdminScreen(
                    currentUserId = userId,
                    state = adminState,
                    onRefresh = adminViewModel::refresh,
                    onRefreshAlliances = adminViewModel::refreshAlliances,
                    onClearAlliancesError = adminViewModel::clearAlliancesError,
                    onSetFilterAlliance = adminViewModel::setFilterAllianceCode,
                    onMemberSearchChange = adminViewModel::setMemberSearchQuery,
                    onAllianceOverlayChange = { publicId, enabled ->
                        adminViewModel.setAllianceOverlayEnabled(publicId, enabled, msgOverlaySaved)
                    },
                    onApprove = { id ->
                        adminViewModel.setMembership(id, "active", msgApproved)
                    },
                    onRemoveFromTeam = { id ->
                        adminViewModel.setMembership(id, "removed", msgRemoved)
                    },
                    onRestorePending = { id ->
                        adminViewModel.setMembership(id, "pending", msgPending)
                    },
                    onSetRole = { memberId, newRole ->
                        adminViewModel.setRole(memberId, newRole, msgRole)
                    },
                    onRename = { memberId, newName ->
                        adminViewModel.setUsername(memberId, newName, msgRename)
                    },
                    onDeleteUser = { memberId ->
                        adminViewModel.deleteUser(memberId, msgDeleted)
                    },
                    onDismissError = adminViewModel::clearError,
                    onRefreshRooms = adminViewModel::refreshRooms,
                    onCreateRoom = { title ->
                        adminViewModel.createChatRoom(title, msgRoomCreated)
                    },
                    onRenameRoom = { roomId, title ->
                        adminViewModel.renameChatRoom(roomId, title, msgRoomRenamed)
                    },
                    onDeleteRoom = { roomId ->
                        adminViewModel.deleteChatRoom(roomId, msgRoomDeleted)
                    },
                    onClearRoomSnack = adminViewModel::clearRoomSnack,
                )
            }
        }
    }
}
