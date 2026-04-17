package com.lastasylum.alliance.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthState(isLoading = true))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        restoreSession()
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
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
                        error = throwable.message ?: "Unable to sign in",
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
                        error = "Session expired. Please sign in again.",
                    )
                }
        }
    }
}
