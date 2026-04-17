package com.lastasylum.alliance.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.auth.AuthViewModel
import com.lastasylum.alliance.ui.auth.AuthViewModelFactory
import com.lastasylum.alliance.ui.auth.LoginScreen
import androidx.compose.runtime.Composable
import com.lastasylum.alliance.ui.chat.ChatViewModelFactory
import com.lastasylum.alliance.ui.theme.LastAsylumTheme

@Composable
fun LastAsylumApp() {
    val context = LocalContext.current
    val appContainer = AppContainer.from(context)
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(
            authRepository = appContainer.authRepository,
        ),
    )
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    LastAsylumTheme {
        if (!authState.isAuthenticated) {
            LoginScreen(
                isLoading = authState.isLoading,
                errorMessage = authState.error,
                onLoginClick = authViewModel::login,
            )
        } else {
            AppNavigation(
                username = authState.user?.username.orEmpty(),
                role = authState.user?.role.orEmpty(),
                onLogout = authViewModel::logout,
                chatViewModelFactory = ChatViewModelFactory(appContainer.chatRepository),
            )
        }
    }
}
