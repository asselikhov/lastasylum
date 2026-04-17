package com.lastasylum.alliance.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lastasylum.alliance.ui.chat.ChatViewModel
import com.lastasylum.alliance.ui.chat.ChatViewModelFactory
import com.lastasylum.alliance.ui.screens.ChatScreen
import com.lastasylum.alliance.ui.screens.OverlayControlScreen
import com.lastasylum.alliance.ui.screens.ProfileScreen

enum class AppTab(val route: String, val title: String) {
    CHAT("chat", "Chat"),
    OVERLAY("overlay", "Overlay"),
    PROFILE("profile", "Profile"),
}

@Composable
fun AppNavigation(
    username: String,
    role: String,
    onLogout: () -> Unit,
    chatViewModelFactory: ChatViewModelFactory,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry.value?.destination

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "OBZHORY • $username ($role)") },
                actions = {
                    FilledIconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Outlined.Logout,
                            contentDescription = "Logout",
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
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
                        label = { Text(text = tab.title) },
                        icon = {
                            when (tab) {
                                AppTab.CHAT -> Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = tab.title,
                                )

                                AppTab.OVERLAY -> Icon(
                                    imageVector = Icons.Outlined.RadioButtonChecked,
                                    contentDescription = tab.title,
                                )

                                AppTab.PROFILE -> Icon(
                                    imageVector = Icons.Outlined.PersonOutline,
                                    contentDescription = tab.title,
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
        }
    }
}
