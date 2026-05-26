package com.lastasylum.alliance.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.lastasylum.alliance.BuildConfig
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.ReadCursorSession
import com.lastasylum.alliance.data.auth.AuthRepository
import com.lastasylum.alliance.data.auth.RegisterResult
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.push.FcmTokenManager
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthViewModel(
    application: Application,
    private val tokenStore: TokenStore,
    private val authRepository: AuthRepository,
    private val chatRoomPreferences: ChatRoomPreferences,
    private val teamForumPreferences: TeamForumPreferences,
    private val userSettingsPreferences: UserSettingsPreferences,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AuthState(isCheckingStoredSession = true))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources

    private var authBootstrapJob: Job? = null

    init {
        authBootstrapJob = viewModelScope.launch {
            val hasRefresh = withContext(Dispatchers.IO) {
                runCatching { tokenStore.getRefreshToken() != null }.getOrDefault(false)
            }
            if (!hasRefresh) {
                _state.value = AuthState(
                    isCheckingStoredSession = false,
                    isLoading = false,
                    isAuthenticated = false,
                )
                return@launch
            }
            restoreSession()
        }
    }

    private fun cancelAuthBootstrap() {
        authBootstrapJob?.cancel()
        authBootstrapJob = null
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, infoMessage = null)
    }

    private fun bindReadCursors(userId: String) {
        ReadCursorSession.bind(
            chatRoomPreferences,
            teamForumPreferences,
            userSettingsPreferences,
            userId,
        )
        val app = AppContainer.from(getApplication())
        viewModelScope.launch {
            ReadCursorSession.syncTeamNewsReadCursor(
                app.usersRepository,
                app.teamsRepository,
                userSettingsPreferences,
            )
        }
    }

    fun login(email: String, password: String) {
        cancelAuthBootstrap()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            authRepository.login(email, password)
                .onSuccess { user ->
                    bindReadCursors(user.id)
                    runCatching { FcmTokenManager.registerWithBackend(getApplication()) }
                    _state.value = AuthState(
                        isCheckingStoredSession = false,
                        isLoading = false,
                        isAuthenticated = true,
                        user = user,
                    )
                }
                .onFailure { throwable ->
                    logAuthFailure("login", throwable)
                    _state.value = AuthState(
                        isCheckingStoredSession = false,
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    fun register(
        email: String,
        password: String,
        serverNumber: Int,
        gameNickname: String,
    ) {
        cancelAuthBootstrap()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            authRepository.register(email, password, serverNumber, gameNickname)
                .onSuccess { result ->
                    when (result) {
                        is RegisterResult.LoggedIn -> {
                            bindReadCursors(result.user.id)
                            runCatching { FcmTokenManager.registerWithBackend(getApplication()) }
                            _state.value = AuthState(
                                isCheckingStoredSession = false,
                                isLoading = false,
                                isAuthenticated = true,
                                user = result.user,
                            )
                        }
                        RegisterResult.PendingApproval -> {
                            _state.value = AuthState(
                                isCheckingStoredSession = false,
                                isLoading = false,
                                isAuthenticated = false,
                                infoMessage = getApplication<Application>().getString(
                                    R.string.auth_register_pending,
                                ),
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    logAuthFailure("register", throwable)
                    _state.value = AuthState(
                        isCheckingStoredSession = false,
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    fun logout() {
        cancelAuthBootstrap()
        viewModelScope.launch {
            runCatching { FcmTokenManager.unregister(getApplication()) }
            authRepository.logout(userId = _state.value.user?.id)
            _state.value = AuthState(
                isCheckingStoredSession = false,
                isLoading = false,
                isAuthenticated = false,
            )
        }
    }

    fun forgotPassword(email: String) {
        cancelAuthBootstrap()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            authRepository.forgotPassword(email)
                .onSuccess {
                    _state.value = AuthState(
                        isCheckingStoredSession = false,
                        isLoading = false,
                        isAuthenticated = false,
                        infoMessage = getApplication<Application>().getString(
                            R.string.auth_forgot_sent,
                        ),
                    )
                }
                .onFailure { throwable ->
                    logAuthFailure("forgotPassword", throwable)
                    _state.value = AuthState(
                        isCheckingStoredSession = false,
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    fun resetPassword(email: String, token: String, newPassword: String) {
        cancelAuthBootstrap()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            authRepository.resetPassword(email, token, newPassword)
                .onSuccess {
                    _state.value = AuthState(
                        isCheckingStoredSession = false,
                        isLoading = false,
                        isAuthenticated = false,
                        infoMessage = getApplication<Application>().getString(
                            R.string.auth_reset_success,
                        ),
                    )
                }
                .onFailure { throwable ->
                    logAuthFailure("resetPassword", throwable)
                    _state.value = AuthState(
                        isCheckingStoredSession = false,
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    private suspend fun restoreSession() {
        if (!authRepository.hasSession()) {
            _state.value = AuthState(
                isCheckingStoredSession = false,
                isLoading = false,
                isAuthenticated = false,
            )
            return
        }

        authRepository.refreshSession()
            .onSuccess { user ->
                bindReadCursors(user.id)
                runCatching { FcmTokenManager.registerWithBackend(getApplication()) }
                _state.value = AuthState(
                    isCheckingStoredSession = false,
                    isLoading = false,
                    isAuthenticated = true,
                    user = user,
                )
            }
            .onFailure { err ->
                if (err is CancellationException) throw err
                logAuthFailure("refreshSession", err)
                authRepository.logout(userId = _state.value.user?.id)
                _state.value = AuthState(
                    isCheckingStoredSession = false,
                    isLoading = false,
                    isAuthenticated = false,
                    error = getApplication<Application>().getString(R.string.session_expired_message),
                )
            }
    }

    private fun logAuthFailure(stage: String, throwable: Throwable) {
        val extra = if (throwable is retrofit2.HttpException) {
            " HTTP ${throwable.code()}"
        } else {
            ""
        }
        Log.e(
            TAG,
            "$stage$extra api=${BuildConfig.API_BASE_URL} :: ${throwable.javaClass.simpleName}: ${throwable.message}",
            throwable,
        )
    }

    private companion object {
        private const val TAG = "SquadRelayAuth"
    }
}
