package com.lastasylum.alliance.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.auth.AuthRepository
import com.lastasylum.alliance.data.auth.RegisterResult
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AuthState(isLoading = true))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources

    init {
        restoreSession()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, infoMessage = null)
    }

    fun login(email: String, password: String) {
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
                    _state.value = AuthState(
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    fun register(username: String, email: String, password: String) {
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
                    _state.value = AuthState(
                        isLoading = false,
                        isAuthenticated = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = AuthState(isLoading = false, isAuthenticated = false)
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            if (!authRepository.hasSession()) {
                _state.value = AuthState(isLoading = false, isAuthenticated = false)
                return@launch
            }

            authRepository.refreshSession()
                .onSuccess { user ->
                    _state.value = AuthState(
                        isLoading = false,
                        isAuthenticated = true,
                        user = user,
                    )
                }
                .onFailure {
                    authRepository.logout()
                    _state.value = AuthState(
                        isLoading = false,
                        isAuthenticated = false,
                        error = getApplication<Application>().getString(R.string.session_expired_message),
                    )
                }
        }
    }
}
