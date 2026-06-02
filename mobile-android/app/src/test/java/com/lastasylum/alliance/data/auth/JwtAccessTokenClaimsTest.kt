package com.lastasylum.alliance.data.auth

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JwtAccessTokenClaimsTest {

    @Before
    fun resetCache() {
        JwtAccessTokenClaims.invalidateCache()
    }

    @Test
    fun isAccessTokenValid_respectsExpWithSkew() {
        val futureExp = System.currentTimeMillis() / 1000L + 3600L
        val token = jwt(sub = "u1", exp = futureExp)
        assertTrue(JwtAccessTokenClaims.isAccessTokenValid(token, skewSeconds = 60L))
    }

    @Test
    fun isAccessTokenValid_falseWhenExpired() {
        val pastExp = System.currentTimeMillis() / 1000L - 120L
        val token = jwt(sub = "u1", exp = pastExp)
        assertFalse(JwtAccessTokenClaims.isAccessTokenValid(token, skewSeconds = 60L))
    }

    @Test
    fun isAccessTokenValid_falseWhenExpMissing() {
        val token = jwt(sub = "u1", exp = null)
        assertFalse(JwtAccessTokenClaims.isAccessTokenValid(token))
    }

    @Test
    fun authUserFromAccessToken_buildsMinimalUser() {
        val token = jwt(
            sub = "user-42",
            email = "player@example.com",
            username = "PlayerOne",
            role = "officer",
            exp = System.currentTimeMillis() / 1000L + 3600L,
        )
        val user = JwtAccessTokenClaims.authUserFromAccessToken(token)
        assertNotNull(user)
        assertEquals("user-42", user!!.id)
        assertEquals("player@example.com", user.email)
        assertEquals("PlayerOne", user.username)
        assertEquals("officer", user.role)
    }

    @Test
    fun authUserFromAccessToken_nullWithoutSubOrEmail() {
        val token = jwt(sub = "", email = "a@b.com", exp = 9999999999L)
        assertNull(JwtAccessTokenClaims.authUserFromAccessToken(token))
    }

    private fun jwt(
        sub: String,
        email: String = "a@example.com",
        username: String = "alice",
        role: String = "member",
        exp: Long?,
    ): String {
        val payload = JSONObject()
            .put("sub", sub)
            .put("email", email)
            .put("username", username)
            .put("role", role)
        if (exp != null) payload.put("exp", exp)
        val header = base64Url("""{"alg":"none","typ":"JWT"}""")
        val body = base64Url(payload.toString())
        return "$header.$body.sig"
    }

    private fun base64Url(json: String): String {
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return encoded.replace('+', '-').replace('/', '_').trimEnd('=')
    }
}
