package com.lastasylum.alliance.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lastasylum.alliance.R
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.nav_title, username, role))
                },
                actions = {
                    FilledIconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Outlined.Logout,
                            contentDescription = stringResource(R.string.cd_logout),
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                visibleTabs.forEach { tab ->
                    val isSelected =
                        currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = isSelected,
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
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppTab.CHAT.route,
        ) {
            composable(AppTab.CHAT.route) {
                val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)
                val chatState by chatViewModel.state.collectAsStateWithLifecycle()
                ChatScreen(
                    contentPadding = contentPadding,
                    username = username,
                    role = role,
                    state = chatState,
                    onSendMessage = chatViewModel::sendMessage,
                )
            }
            composable(AppTab.OVERLAY.route) {
                OverlayControlScreen(
                    contentPadding = contentPadding,
                    role = role,
                )
            }
            composable(AppTab.PROFILE.route) {
                ProfileScreen(
                    contentPadding = contentPadding,
                    username = username,
                    role = role,
                )
            }
            composable(AppTab.ADMIN.route) {
                val adminViewModel: AdminViewModel = viewModel(factory = adminViewModelFactory)
                val adminState by adminViewModel.state.collectAsStateWithLifecycle()
                AdminScreen(
                    contentPadding = contentPadding,
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
                )
            }
        }
    }
}
