package com.lastasylum.alliance.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

enum class AppTab(val route: String, val titleRes: Int) {
    CHAT("chat", R.string.tab_chat),
    OVERLAY("overlay", R.string.tab_overlay),
    PROFILE("profile", R.string.tab_profile),
    ADMIN("admin", R.string.tab_admin),
}

@Composable
fun AppNavigation(
    userId: String,
    username: String,
    role: String,
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

    val visibleTabs = remember(role) {
        if (role == "R5") {
            AppTab.entries.toList()
        } else {
            AppTab.entries.filter { it != AppTab.ADMIN }
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
            val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
            if (!keyboardOpen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                ) {
                    visibleTabs.forEach { tab ->
                        val isSelected =
                            currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            alwaysShowLabel = false,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            label = { Text(text = stringResource(tab.titleRes)) },
                            icon = {
                                when (tab) {
                                    AppTab.CHAT -> Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = stringResource(tab.titleRes),
                                    )

                                    AppTab.OVERLAY -> Icon(
                                        imageVector = Icons.Outlined.RadioButtonChecked,
                                        contentDescription = stringResource(tab.titleRes),
                                    )

                                    AppTab.PROFILE -> Icon(
                                        imageVector = Icons.Outlined.PersonOutline,
                                        contentDescription = stringResource(tab.titleRes),
                                    )

                                    AppTab.ADMIN -> Icon(
                                        imageVector = Icons.Outlined.AdminPanelSettings,
                                        contentDescription = stringResource(tab.titleRes),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppTab.CHAT.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(AppTab.CHAT.route) {
                val chatState by chatViewModel.state.collectAsStateWithLifecycle()
                val draftMessage by chatViewModel.draftMessage.collectAsStateWithLifecycle()
                val typingPeers by chatViewModel.typingPeers.collectAsStateWithLifecycle()
                ChatScreen(
                    state = chatState,
                    typingPeers = typingPeers,
                    draftMessage = draftMessage,
                    onSelectRoom = chatViewModel::selectRoom,
                    onClearError = chatViewModel::clearError,
                    onLoadOlder = chatViewModel::loadOlderMessages,
                    onDraftChange = chatViewModel::setDraftMessage,
                    onSendDraft = chatViewModel::sendDraftMessage,
                    onReplyToMessage = chatViewModel::beginReplyToMessage,
                    onClearReply = chatViewModel::clearReplyToMessage,
                    onOpenMessageActions = chatViewModel::openMessageActions,
                    onDismissMessageActions = chatViewModel::dismissMessageActions,
                    onRequestDeleteMessage = chatViewModel::requestDeleteMessage,
                    onDismissDeleteMessage = chatViewModel::dismissDeleteMessage,
                    onConfirmDeleteMessage = chatViewModel::confirmDeleteMessage,
                    onRetrySendFailure = chatViewModel::retrySendFailure,
                    onDismissSendFailure = chatViewModel::dismissSendFailure,
                )
            }
            composable(AppTab.OVERLAY.route) {
                OverlayControlScreen(role = role)
            }
            composable(AppTab.PROFILE.route) {
                ProfileScreen(
                    username = username,
                    role = role,
                    onLogout = onLogout,
                )
            }
            composable(AppTab.ADMIN.route) {
                val adminViewModel: AdminViewModel = viewModel(factory = adminViewModelFactory)
                val adminState by adminViewModel.state.collectAsStateWithLifecycle()
                AdminScreen(
                    currentUserId = userId,
                    state = adminState,
                    onRefresh = adminViewModel::refresh,
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
