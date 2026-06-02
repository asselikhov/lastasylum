package com.lastasylum.alliance.data.auth

import android.util.Base64
import org.json.JSONObject

/** Parses SquadRelay JWT access token payload (sub, role, …). */
object JwtAccessTokenClaims {
    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedPayload: JSONObject? = null

    fun payload(accessToken: String?): JSONObject? {
        if (accessToken.isNullOrBlank()) {
            cachedToken = null
            cachedPayload = null
            return null
        }
        if (accessToken == cachedToken) return cachedPayload
        val parts = accessToken.split('.')
        if (parts.size < 2) {
            cachedToken = accessToken
            cachedPayload = null
            return null
        }
        var segment = parts[1].replace('-', '+').replace('_', '/')
        when (segment.length % 4) {
            2 -> segment += "=="
            3 -> segment += "="
            else -> {}
        }
        val json = runCatching {
            JSONObject(String(Base64.decode(segment, Base64.DEFAULT), Charsets.UTF_8))
        }.getOrNull()
        cachedToken = accessToken
        cachedPayload = json
        return json
    }

    fun sub(accessToken: String?): String? =
        payload(accessToken)?.optString("sub", "")?.takeIf { it.isNotBlank() }

    fun role(accessToken: String?): String? =
        payload(accessToken)?.optString("role", "")?.takeIf { it.isNotBlank() }

    fun email(accessToken: String?): String? =
        payload(accessToken)?.optString("email", "")?.takeIf { it.isNotBlank() }

    fun username(accessToken: String?): String? =
        payload(accessToken)?.optString("username", "")?.takeIf { it.isNotBlank() }

    /** JWT `exp` claim (seconds since epoch), or null if missing/unparseable. */
    fun expiresAtEpochSeconds(accessToken: String?): Long? {
        val exp = payload(accessToken)?.opt("exp") ?: return null
        return when (exp) {
            is Number -> exp.toLong()
            is String -> exp.toLongOrNull()
            else -> null
        }
    }

    /**
     * True when [accessToken] has a future `exp` (with [skewSeconds] leeway).
     * Missing `exp` is treated as invalid for fast-path bootstrap.
     */
    fun isAccessTokenValid(accessToken: String?, skewSeconds: Long = DEFAULT_EXPIRY_SKEW_SECONDS): Boolean {
        if (accessToken.isNullOrBlank()) return false
        val exp = expiresAtEpochSeconds(accessToken) ?: return false
        val nowSec = System.currentTimeMillis() / 1000L
        return exp > nowSec + skewSeconds
    }

    /** Minimal [AuthUser] from JWT claims when disk cache is absent. */
    fun authUserFromAccessToken(accessToken: String?): AuthUser? {
        val sub = sub(accessToken) ?: return null
        val email = email(accessToken) ?: return null
        val username = username(accessToken) ?: email
        val role = role(accessToken) ?: "member"
        return AuthUser(
            id = sub,
            email = email,
            username = username,
            role = role,
        )
    }

    fun invalidateCache() {
        cachedToken = null
        cachedPayload = null
    }

    const val DEFAULT_EXPIRY_SKEW_SECONDS = 60L
}
