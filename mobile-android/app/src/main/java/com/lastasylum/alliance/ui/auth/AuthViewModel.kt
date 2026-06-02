package com.lastasylum.alliance.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.lastasylum.alliance.BuildConfig
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.ReadCursorSession
import com.lastasylum.alliance.data.auth.AuthRepository
import com.lastasylum.alliance.data.auth.AuthUser
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.auth.RegisterResult
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.push.FcmTokenManager
import com.lastasylum.alliance.ui.SESSION_BOOTSTRAP_MAX_MS
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

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
    private var backgroundSessionRefreshJob: Job? = null

    init {
        authBootstrapJob = viewModelScope.launch {
            val bootstrapStartMs = System.currentTimeMillis()
            var tokenProbeMs = 0L
            val hasRefresh = withContext(Dispatchers.IO) {
                val probeStart = System.currentTimeMillis()
                val found = runCatching { tokenStore.getRefreshToken() != null }.getOrDefault(false)
                tokenProbeMs = System.currentTimeMillis() - probeStart
                found
            }
            if (!hasRefresh) {
                logBootstrapDebug(
                    bootstrapStartMs = bootstrapStartMs,
                    tokenProbeMs = tokenProbeMs,
                    path = "no_refresh",
                )
                _state.value = AuthState(
                    isCheckingStoredSession = false,
                    isLoading = false,
                    isAuthenticated = false,
                )
                return@launch
            }
            withContext(Dispatchers.IO) {
                restoreSession(bootstrapStartMs, tokenProbeMs)
            }
        }
    }

    private fun cancelAuthBootstrap() {
        authBootstrapJob?.cancel()
        authBootstrapJob = null
        backgroundSessionRefreshJob?.cancel()
        backgroundSessionRefreshJob = null
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

    private fun registerFcmInBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { FcmTokenManager.registerWithBackend(getApplication()) }
        }
    }

    private fun completeAuthenticated(user: AuthUser) {
        _state.value = AuthState(
            isCheckingStoredSession = false,
            isLoading = false,
            isAuthenticated = true,
            user = user,
        )
    }

    fun login(email: String, password: String) {
        cancelAuthBootstrap()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            authRepository.login(email, password)
                .onSuccess { user ->
                    bindReadCursors(user.id)
                    registerFcmInBackground()
                    completeAuthenticated(user)
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
                            registerFcmInBackground()
                            completeAuthenticated(result.user)
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

    private suspend fun restoreSession(bootstrapStartMs: Long, tokenProbeMs: Long) {
        if (!authRepository.hasSession()) {
            logBootstrapDebug(
                bootstrapStartMs = bootstrapStartMs,
                tokenProbeMs = tokenProbeMs,
                path = "no_session",
            )
            _state.value = AuthState(
                isCheckingStoredSession = false,
                isLoading = false,
                isAuthenticated = false,
            )
            return
        }

        val access = tokenStore.getAccessToken()
        val fastUser = authRepository.resolveSessionUserForFastPath(access)
        if (
            !access.isNullOrBlank() &&
            JwtAccessTokenClaims.isAccessTokenValid(access) &&
            fastUser != null
        ) {
            bindReadCursors(fastUser.id)
            completeAuthenticated(fastUser)
            logBootstrapDebug(
                bootstrapStartMs = bootstrapStartMs,
                tokenProbeMs = tokenProbeMs,
                refreshMs = 0L,
                path = "fast_path",
            )
            scheduleBackgroundSessionRefresh()
            return
        }

        val refreshStartMs = System.currentTimeMillis()
        val refreshResult = withTimeoutOrNull(SESSION_BOOTSTRAP_MAX_MS) {
            authRepository.refreshSession()
        }
        val refreshMs = System.currentTimeMillis() - refreshStartMs

        when {
            refreshResult == null -> {
                logBootstrapDebug(
                    bootstrapStartMs = bootstrapStartMs,
                    tokenProbeMs = tokenProbeMs,
                    refreshMs = refreshMs,
                    path = "refresh_timeout",
                )
                if (tryAuthenticateFromFastPath(access)) {
                    scheduleBackgroundSessionRefresh()
                    return
                }
                _state.value = AuthState(
                    isCheckingStoredSession = false,
                    isLoading = false,
                    isAuthenticated = false,
                    error = getApplication<Application>().getString(
                        R.string.launch_splash_session_timeout,
                    ),
                )
            }
            refreshResult.isSuccess -> {
                val user = refreshResult.getOrThrow()
                bindReadCursors(user.id)
                completeAuthenticated(user)
                logBootstrapDebug(
                    bootstrapStartMs = bootstrapStartMs,
                    tokenProbeMs = tokenProbeMs,
                    refreshMs = refreshMs,
                    path = "refresh_ok",
                )
            }
            else -> {
                val err = refreshResult.exceptionOrNull()!!
                if (err is CancellationException) throw err
                logAuthFailure("refreshSession", err)
                logBootstrapDebug(
                    bootstrapStartMs = bootstrapStartMs,
                    tokenProbeMs = tokenProbeMs,
                    refreshMs = refreshMs,
                    path = "refresh_failed",
                )
                authRepository.logout(userId = _state.value.user?.id)
                _state.value = AuthState(
                    isCheckingStoredSession = false,
                    isLoading = false,
                    isAuthenticated = false,
                    error = getApplication<Application>().getString(R.string.session_expired_message),
                )
            }
        }
    }

    private fun tryAuthenticateFromFastPath(access: String?): Boolean {
        if (access.isNullOrBlank() || !JwtAccessTokenClaims.isAccessTokenValid(access)) {
            return false
        }
        val user = authRepository.resolveSessionUserForFastPath(access) ?: return false
        bindReadCursors(user.id)
        completeAuthenticated(user)
        return true
    }

    private fun scheduleBackgroundSessionRefresh() {
        backgroundSessionRefreshJob?.cancel()
        backgroundSessionRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            val refreshStart = System.currentTimeMillis()
            authRepository.refreshSession()
                .onSuccess { user ->
                    withContext(Dispatchers.Main) {
                        bindReadCursors(user.id)
                        if (_state.value.isAuthenticated) {
                            _state.value = _state.value.copy(user = user)
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "background_refresh_ok ms=${System.currentTimeMillis() - refreshStart}",
                        )
                    }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    logAuthFailure("backgroundRefreshSession", err)
                    if (shouldLogoutOnRefreshFailure(err)) {
                        withContext(Dispatchers.Main) {
                            authRepository.logout(userId = _state.value.user?.id)
                            _state.value = AuthState(
                                isCheckingStoredSession = false,
                                isLoading = false,
                                isAuthenticated = false,
                                error = getApplication<Application>().getString(
                                    R.string.session_expired_message,
                                ),
                            )
                        }
                    }
                }
        }
    }

    private fun shouldLogoutOnRefreshFailure(err: Throwable): Boolean =
        err is HttpException && err.code() in UNAUTHORIZED_REFRESH_CODES

    private fun logBootstrapDebug(
        bootstrapStartMs: Long,
        tokenProbeMs: Long,
        refreshMs: Long = 0L,
        path: String,
    ) {
        if (!BuildConfig.DEBUG) return
        val totalMs = System.currentTimeMillis() - bootstrapStartMs
        Log.d(
            TAG,
            "session_bootstrap path=$path token_probe_ms=$tokenProbeMs refresh_ms=$refreshMs total_ms=$totalMs",
        )
    }

    private fun logAuthFailure(stage: String, throwable: Throwable) {
        val extra = if (throwable is HttpException) {
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
        private val UNAUTHORIZED_REFRESH_CODES = setOf(401, 403)
    }
}
