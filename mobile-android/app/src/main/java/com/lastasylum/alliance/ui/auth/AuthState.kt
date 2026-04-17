package com.lastasylum.alliance.ui.auth

import com.lastasylum.alliance.data.auth.AuthUser

data class AuthState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: AuthUser? = null,
    val error: String? = null,
)
