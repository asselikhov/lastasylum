package com.lastasylum.alliance.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.auth.AuthScreen
import com.lastasylum.alliance.ui.auth.AuthViewModel
import com.lastasylum.alliance.ui.auth.AuthViewModelFactory
import com.lastasylum.alliance.ui.admin.AdminViewModelFactory
import com.lastasylum.alliance.ui.chat.ChatViewModelFactory
import com.lastasylum.alliance.ui.theme.SquadRelayTheme
import com.lastasylum.alliance.R
import com.lastasylum.alliance.update.fetchNewerApkDownloadUrl
import com.lastasylum.alliance.update.openApkDownload

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
    val surfaceContext = LocalContext.current
    var pendingApkUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { fetchNewerApkDownloadUrl() }.getOrNull()?.let { pendingApkUrl = it }
    }

    SquadRelayTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize()) {
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
                            chatRoomPreferences = appContainer.chatRoomPreferences,
                            currentUserId = authState.user?.id.orEmpty(),
                        ),
                        adminViewModelFactory = AdminViewModelFactory(
                            application = application,
                            usersRepository = appContainer.usersRepository,
                            chatRoomsRepository = appContainer.chatRoomsRepository,
                        ),
                    )
                }
            }

            pendingApkUrl?.let { url ->
                AlertDialog(
                    onDismissRequest = { pendingApkUrl = null },
                    title = { Text(stringResource(R.string.update_dialog_title)) },
                    text = { Text(stringResource(R.string.update_dialog_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                surfaceContext.openApkDownload(url)
                                pendingApkUrl = null
                            },
                        ) {
                            Text(stringResource(R.string.update_dialog_download))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingApkUrl = null }) {
                            Text(stringResource(R.string.update_dialog_later))
                        }
                    },
                )
            }
        }
    }
}
