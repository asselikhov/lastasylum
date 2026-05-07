package com.lastasylum.alliance.ui.auth

import com.lastasylum.alliance.data.auth.AuthUser

data class AuthState(
    /** True while validating refresh token on cold start (avoid flashing auth form). */
    val isCheckingStoredSession: Boolean = false,
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: AuthUser? = null,
    val error: String? = null,
    /** Non-error info, e.g. registration pending admin approval. */
    val infoMessage: String? = null,
)
