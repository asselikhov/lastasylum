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
            409 -> parsed ?: resources.getString(R.string.err_duplicate_value)
            400 -> parsed ?: resources.getString(R.string.err_form_check)
            403 -> when {
                raw.contains("GLOBAL_CHAT_TEAM_PROFILE_REQUIRED", ignoreCase = true) ->
                    resources.getString(R.string.chat_global_team_required)
                raw.contains("temporarily muted", ignoreCase = true) ->
                    resources.getString(R.string.err_chat_muted)
                raw.contains("Chat is not available", ignoreCase = true) ->
                    resources.getString(R.string.err_chat_unavailable)
                raw.contains("Room is not available", ignoreCase = true) ->
                    resources.getString(R.string.err_chat_room_unavailable)
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
    english.contains("This team name is already taken", ignoreCase = true) ->
        resources.getString(R.string.err_team_name_taken)
    english.contains("This team tag is already taken", ignoreCase = true) ->
        resources.getString(R.string.err_team_tag_taken)
    english.contains("GLOBAL_CHAT_TEAM_PROFILE_REQUIRED", ignoreCase = true) ->
        resources.getString(R.string.chat_global_team_required)
    english.contains("Only image uploads are supported", ignoreCase = true) ->
        resources.getString(R.string.chat_attachment_only_images_server)
    english.contains("CHAT_ATTACHMENT_R2_PUT_FAILED", ignoreCase = true) ||
        english.contains("TEAM_NEWS_ATTACHMENT_R2_PUT_FAILED", ignoreCase = true) ->
        resources.getString(R.string.chat_attachment_r2_put_failed)
    english.contains("Image must be uploaded by the message sender", ignoreCase = true) ||
        english.contains("Image not found for this team", ignoreCase = true) ||
        english.contains("Invalid image file id", ignoreCase = true) ->
        resources.getString(R.string.chat_attachment_read_failed)
    english.contains("Only alliance admins", ignoreCase = true) ||
        english.contains("may upload APK", ignoreCase = true) ->
        resources.getString(R.string.chat_attachment_apk_only_server)
    english.contains("Only APK files", ignoreCase = true) ||
        english.contains("APK for R5", ignoreCase = true) ->
        resources.getString(R.string.chat_attachment_apk_invalid)
    english.contains("Poll requires a question", ignoreCase = true) ||
        english.contains("at least two options", ignoreCase = true) ->
        resources.getString(R.string.team_news_poll_invalid)
    english.contains("Title and body are required without a poll", ignoreCase = true) ->
        resources.getString(R.string.team_news_fill_required)
    english.contains("Only squad roles R4 and R5 can publish", ignoreCase = true) ->
        resources.getString(R.string.team_news_publish_forbidden)
    english.contains("Only squad role R5 can assign rank R5", ignoreCase = true) ->
        resources.getString(R.string.team_squad_assign_r5_forbidden)
    english.contains("Only squad role R5 can change the rank of an R5 member", ignoreCase = true) ->
        resources.getString(R.string.team_squad_change_r5_member_forbidden)
    english.contains("Only squad roles R4 and R5 can change member ranks", ignoreCase = true) ->
        resources.getString(R.string.team_squad_change_rank_forbidden)
    english.contains("Join request already pending", ignoreCase = true) ->
        resources.getString(R.string.profile_player_team_join_already_pending)
    english.contains("You already belong to a team", ignoreCase = true) ->
        resources.getString(R.string.profile_player_team_join_already_in_team)
    english.contains("Already a member", ignoreCase = true) ->
        resources.getString(R.string.profile_player_team_join_already_member)
    english.contains("TEAM_JOIN_SERVER_MISMATCH", ignoreCase = true) ->
        resources.getString(R.string.err_team_join_server_mismatch)
    english.contains("ACTIVE_GAME_SERVER_REQUIRED", ignoreCase = true) ->
        resources.getString(R.string.err_active_game_server_required)
    english.contains("User already joined another team", ignoreCase = true) ->
        resources.getString(R.string.profile_join_user_already_in_team)
    else -> english
}
