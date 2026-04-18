package com.lastasylum.alliance.data.auth

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String,
    val email: String,
    val username: String,
    val role: String,
    val membershipStatus: String? = null,
    val allianceName: String? = null,
    val presenceStatus: String? = null,
    val lastPresenceAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUser,
)

/** Register may return tokens or only [approvalRequired] + [user]. */
@JsonClass(generateAdapter = true)
data class RegisterEnvelope(
    val approvalRequired: Boolean? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: AuthUser? = null,
)

sealed class RegisterResult {
    data class LoggedIn(val user: AuthUser) : RegisterResult()
    data object PendingApproval : RegisterResult()
}

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(
    val email: String,
)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(
    val email: String,
    val token: String,
    val newPassword: String,
)

@JsonClass(generateAdapter = true)
data class OkEnvelope(
    val ok: Boolean? = null,
)
