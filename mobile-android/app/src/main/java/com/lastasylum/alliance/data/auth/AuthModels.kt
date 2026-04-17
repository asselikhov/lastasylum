package com.lastasylum.alliance.data.auth

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RefreshRequest(
    val refreshToken: String,
)

data class AuthUser(
    val id: String,
    val email: String,
    val username: String,
    val role: String,
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUser,
)
