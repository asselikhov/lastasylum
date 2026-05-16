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

    fun invalidateCache() {
        cachedToken = null
        cachedPayload = null
    }
}
