package com.lastasylum.alliance.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.lastasylum.alliance.BuildConfig
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.auth.AuthRepository
import com.lastasylum.alliance.data.auth.RegisterResult
import com.lastasylum.alliance.push.FcmTokenManager
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository,
) : AndroidViewModel(application) {
    /** Не держим экран в «загрузке» при фоновом refresh — иначе нельзя войти другим аккаунтом, пока таймаутит старый refresh. */
    private val _state = MutableStateFlow(AuthState(isLoading = false))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources

    private var sessionRestoreJob: Job? = null

    init {
        sessionRestoreJob = viewModelScope.launch {
            restoreSession()
        }
    }

    private fun cancelSilentSessionRestore() {
        sessionRestoreJob?.cancel()
        sessionRestoreJob = null
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, infoMessage = null)
    }

    fun login(email: String, password: String) {
        cancelSilentSessionRestore()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            authRepository.login(email, password)
                .onSuccess { user ->
                    _state.value = AuthState(
                        isLoading = false,
                        isAuthenticated = true,
                        user = user,
                    )
                }
                .onFailure { throwable ->
                    logAuthFailure("login", throwable)
                    _state.value = AuthState(
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    fun register(username: String, email: String, password: String) {
        cancelSilentSessionRestore()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            authRepository.register(username, email, password)
                .onSuccess { result ->
                    when (result) {
                        is RegisterResult.LoggedIn -> {
                            _state.value = AuthState(
                                isLoading = false,
                                isAuthenticated = true,
                                user = result.user,
                            )
                        }
                        RegisterResult.PendingApproval -> {
                            _state.value = AuthState(
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
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    fun logout() {
        cancelSilentSessionRestore()
        viewModelScope.launch {
            runCatching { FcmTokenManager.unregister(getApplication()) }
            authRepository.logout()
            _state.value = AuthState(isLoading = false, isAuthenticated = false)
        }
    }

    fun forgotPassword(email: String) {
        cancelSilentSessionRestore()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            authRepository.forgotPassword(email)
                .onSuccess {
                    _state.value = AuthState(
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
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    fun resetPassword(email: String, token: String, newPassword: String) {
        cancelSilentSessionRestore()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            authRepository.resetPassword(email, token, newPassword)
                .onSuccess {
                    _state.value = AuthState(
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
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    private suspend fun restoreSession() {
        if (!authRepository.hasSession()) {
            _state.value = AuthState(isLoading = false, isAuthenticated = false)
            return
        }

        authRepository.refreshSession()
            .onSuccess { user ->
                _state.value = AuthState(
                    isLoading = false,
                    isAuthenticated = true,
                    user = user,
                )
            }
            .onFailure { err ->
                logAuthFailure("refreshSession", err)
                authRepository.logout()
                _state.value = AuthState(
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
