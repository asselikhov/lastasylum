package com.lastasylum.alliance.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.di.ChatViewModelRegistry
import androidx.compose.runtime.DisposableEffect
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayRuntimeScheduler
import com.lastasylum.alliance.ui.auth.AuthScreen
import com.lastasylum.alliance.ui.auth.AuthViewModel
import com.lastasylum.alliance.ui.auth.AuthViewModelFactory
import com.lastasylum.alliance.ui.admin.AdminViewModelFactory
import com.lastasylum.alliance.ui.components.AtmosphericBackground
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import com.lastasylum.alliance.ui.theme.SquadRelayTheme
import com.lastasylum.alliance.R
import com.lastasylum.alliance.push.FcmTokenManager
import com.lastasylum.alliance.push.PushTokenRegistrationEffect
import com.lastasylum.alliance.ui.chat.ChatViewModel
import com.lastasylum.alliance.ui.chat.ChatViewModelFactory
import com.lastasylum.alliance.ui.onboarding.PermissionOnboardingGate
import com.lastasylum.alliance.update.fetchNewerApkDownloadUrl
import com.lastasylum.alliance.update.openApkDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SquadRelayApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val application = context.applicationContext as Application
    val appContainer = AppContainer.from(context)
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(
            application = application,
            appContainer = appContainer,
        ),
    )
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val surfaceContext = LocalContext.current
    var pendingApkUrl by remember { mutableStateOf<String?>(null) }
    var postAuthSplashComplete by remember { mutableStateOf(false) }

    LaunchedEffect(authState.isAuthenticated) {
        if (!authState.isAuthenticated) {
            postAuthSplashComplete = false
        }
    }

    LaunchedEffect(Unit) {
        // Не конкурируем с восстановлением сессии и первым bootstrap чата.
        delay(4_000)
        val url = withContext(Dispatchers.IO) {
            runCatching { fetchNewerApkDownloadUrl() }.getOrNull()
        }
        pendingApkUrl = url
    }

    LaunchedEffect(authState.isAuthenticated) {
        if (!authState.isAuthenticated) {
            runCatching {
                AppContainer.from(application).chatRepository.resetRealtimeForLogout()
                CombatOverlayService.stopRuntime(application)
                OverlayRuntimeScheduler.cancel(application)
            }
        }
    }

    LaunchedEffect(authState.user?.id, authState.isAuthenticated) {
        if (authState.isAuthenticated && authState.user?.id?.isNotBlank() == true) {
            withContext(Dispatchers.IO) {
                runCatching { FcmTokenManager.registerWithBackend(application) }
            }
        }
    }

    SquadRelayTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize()) {
                AtmosphericBackground(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize()) {
                    when {
                        authState.isCheckingStoredSession -> {
                            SessionBootstrapSplash()
                        }
                        !authState.isAuthenticated -> {
                            AuthScreen(
                                isLoading = authState.isLoading,
                                errorMessage = authState.error,
                                infoMessage = authState.infoMessage,
                                onLoginClick = authViewModel::login,
                                onRegisterClick = { serverNumber, gameNickname, email, password ->
                                    authViewModel.register(
                                        email,
                                        password,
                                        serverNumber,
                                        gameNickname,
                                    )
                                },
                                onForgotPassword = authViewModel::forgotPassword,
                                onResetPassword = authViewModel::resetPassword,
                                onClearError = authViewModel::clearError,
                            )
                        }
                        else -> {
                            val chatViewModel: ChatViewModel = viewModel(
                                viewModelStoreOwner = activity,
                                key = "alliance_chat",
                                factory = ChatViewModelFactory(
                                    application = application,
                                    repository = appContainer.chatRepository,
                                    chatRoomPreferences = appContainer.chatRoomPreferences,
                                    teamForumPreferences = appContainer.teamForumPreferences,
                                    usersRepository = appContainer.usersRepository,
                                    currentUserId = authState.user?.id.orEmpty(),
                                    currentUserRole = authState.user?.role.orEmpty(),
                                ),
                            )
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
                            if (!postAuthSplashComplete) {
                                PostAuthLaunchSplash(
                                    onComplete = { postAuthSplashComplete = true },
                                    warmup = {
                                        runAppLaunchWarmup(
                                            application = application,
                                            container = appContainer,
                                            chatViewModel = chatViewModel,
                                        )
                                    },
                                )
                            } else {
                            Box(Modifier.fillMaxSize()) {
                                AppNavigation(
                                    userId = authState.user?.id.orEmpty(),
                                    username = authState.user?.username.orEmpty(),
                                    role = authState.user?.role.orEmpty(),
                                    overlayTabVisible = authState.user?.overlayTabVisible == true,
                                    onLogout = authViewModel::logout,
                                    chatViewModelFactory = ChatViewModelFactory(
                                        application = application,
                                        repository = appContainer.chatRepository,
                                        chatRoomPreferences = appContainer.chatRoomPreferences,
                                        teamForumPreferences = appContainer.teamForumPreferences,
                                        usersRepository = appContainer.usersRepository,
                                        currentUserId = authState.user?.id.orEmpty(),
                                        currentUserRole = authState.user?.role.orEmpty(),
                                    ),
                                    adminViewModelFactory = AdminViewModelFactory(
                                        application = application,
                                        usersRepository = appContainer.usersRepository,
                                        adminRepository = appContainer.adminRepository,
                                    ),
                                )
                                PushTokenRegistrationEffect(
                                    enabled = authState.isAuthenticated && postAuthSplashComplete,
                                )
                                PermissionOnboardingGate()
                            }
                            }
                        }
                    }
                }
            }

            pendingApkUrl?.let { url ->
                AlertDialog(
                    onDismissRequest = { pendingApkUrl = null },
                    containerColor = SquadRelaySurfaces.dialogColor(),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = {
                        Text(
                            text = stringResource(R.string.update_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.update_dialog_body),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
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
