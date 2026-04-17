package com.lastasylum.alliance.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.auth.AuthScreen
import com.lastasylum.alliance.ui.auth.AuthViewModel
import com.lastasylum.alliance.ui.auth.AuthViewModelFactory
import com.lastasylum.alliance.ui.admin.AdminViewModelFactory
import com.lastasylum.alliance.ui.chat.ChatViewModelFactory
import com.lastasylum.alliance.ui.theme.SquadRelayTheme

@Composable
fun SquadRelayApp() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val appContainer = AppContainer.from(context)
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(
            application = application,
            authRepository = appContainer.authRepository,
        ),
    )
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    SquadRelayTheme {
        if (!authState.isAuthenticated) {
            AuthScreen(
                isLoading = authState.isLoading,
                errorMessage = authState.error,
                infoMessage = authState.infoMessage,
                onLoginClick = authViewModel::login,
                onRegisterClick = authViewModel::register,
                onClearError = authViewModel::clearError,
            )
        } else {
            AppNavigation(
                userId = authState.user?.id.orEmpty(),
                username = authState.user?.username.orEmpty(),
                role = authState.user?.role.orEmpty(),
                onLogout = authViewModel::logout,
                chatViewModelFactory = ChatViewModelFactory(
                    application = application,
                    repository = appContainer.chatRepository,
                ),
                adminViewModelFactory = AdminViewModelFactory(
                    application = application,
                    usersRepository = appContainer.usersRepository,
                ),
            )
        }
    }
}
