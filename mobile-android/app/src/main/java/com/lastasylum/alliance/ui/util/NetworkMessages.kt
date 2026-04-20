package com.lastasylum.alliance.ui.util

import android.content.res.Resources
import com.lastasylum.alliance.R
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.net.ConnectException
import java.util.Locale
import javax.net.ssl.SSLHandshakeException

fun Throwable.toUserMessageRu(resources: Resources): String {
    if (this is HttpException) {
        val code = code()
        val raw = response()?.errorBody()?.string().orEmpty()
        val parsed = parseNestMessage(raw, resources)
        return when (code) {
            401 -> when {
                raw.contains("Invalid credentials", ignoreCase = true) ->
                    resources.getString(R.string.err_invalid_credentials)
                raw.contains("Invalid refresh", ignoreCase = true) ||
                    raw.contains("Refresh session is not active", ignoreCase = true) ->
                    resources.getString(R.string.err_session_refresh)
                else -> parsed ?: resources.getString(R.string.err_auth_with_code, code)
            }
            409 -> resources.getString(R.string.err_email_taken)
            400 -> parsed ?: resources.getString(R.string.err_form_check)
            403 -> when {
                raw.contains("GLOBAL_CHAT_TEAM_PROFILE_REQUIRED", ignoreCase = true) ->
                    resources.getString(R.string.chat_global_team_required)
                raw.contains("Account pending administrator approval", ignoreCase = true) ->
                    resources.getString(R.string.err_pending_approval)
                else -> resources.getString(R.string.err_forbidden)
            }
            429 -> resources.getString(R.string.err_rate_limit)
            502, 503 -> parsed ?: resources.getString(R.string.err_server_temp)
            in 500..599 -> parsed ?: resources.getString(R.string.err_server_generic)
            else -> parsed ?: resources.getString(R.string.err_network_with_code, code)
        }
    }
    if (this is java.net.UnknownHostException) {
        return resources.getString(R.string.err_no_connection)
    }
    if (this is java.net.SocketTimeoutException) {
        return resources.getString(R.string.err_timeout)
    }
    if (this is ConnectException) {
        return resources.getString(R.string.err_no_connection)
    }
    if (this is SSLHandshakeException) {
        return resources.getString(R.string.err_tls_handshake)
    }
    if (this is java.io.IOException) {
        val m = message.orEmpty().lowercase(Locale.ROOT)
        if (m.contains("cleartext") && m.contains("not permitted")) {
            return resources.getString(R.string.err_cleartext_blocked)
        }
    }
    return message?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.err_unknown)
}

private fun parseNestMessage(raw: String, resources: Resources): String? {
    if (raw.isBlank()) return null
    return runCatching {
        val obj = JSONObject(raw)
        when (val m = obj.opt("message")) {
            is String -> m.takeIf { it.isNotBlank() }?.let { translateKnownServerMessage(it, resources) }
            is JSONArray -> {
                val first = (0 until m.length())
                    .mapNotNull { i -> m.optString(i).takeIf(String::isNotBlank) }
                    .firstOrNull()
                first?.let { translateKnownServerMessage(it, resources) }
            }
            else -> null
        }
    }.getOrNull()
}

private fun translateKnownServerMessage(english: String, resources: Resources): String = when {
    english.contains("Email is already in use", ignoreCase = true) ->
        resources.getString(R.string.err_email_taken_short)
    english.contains("Invalid credentials", ignoreCase = true) ->
        resources.getString(R.string.err_invalid_credentials)
    english.contains("username", ignoreCase = true) &&
        (english.contains("longer", ignoreCase = true) || english.contains("shorter", ignoreCase = true)) ->
        resources.getString(R.string.err_validation_username)
    english.contains("email", ignoreCase = true) && english.contains("valid", ignoreCase = true) ->
        resources.getString(R.string.err_validation_email)
    english.contains("password", ignoreCase = true) &&
        (english.contains("longer", ignoreCase = true) || english.contains("shorter", ignoreCase = true)) ->
        resources.getString(R.string.err_validation_password)
    english.contains("Username is already taken", ignoreCase = true) ->
        resources.getString(R.string.err_username_taken)
    english.contains("GLOBAL_CHAT_TEAM_PROFILE_REQUIRED", ignoreCase = true) ->
        resources.getString(R.string.chat_global_team_required)
    english.contains("Only image uploads are supported", ignoreCase = true) ->
        resources.getString(R.string.chat_attachment_only_images_server)
    english.contains("CHAT_ATTACHMENT_R2_PUT_FAILED", ignoreCase = true) ->
        resources.getString(R.string.chat_attachment_r2_put_failed)
    else -> english
}
