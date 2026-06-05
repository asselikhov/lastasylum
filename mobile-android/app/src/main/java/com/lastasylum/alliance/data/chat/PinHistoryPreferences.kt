package com.lastasylum.alliance.data.chat

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
internal data class CachedPinHistory(val entries: List<PinnedMessagePreviewDto>)

/** Persists client-side pin history per chat room or forum topic. */
class PinHistoryPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter =
        moshi.adapter(CachedPinHistory::class.java)

    @Volatile
    private var activeUserId: String? = null

    fun bindUser(userId: String) {
        val id = userId.trim()
        activeUserId = id.ifEmpty { null }
    }

    fun chatScopeKey(roomId: String): String {
        val uid = activeUserId?.trim().orEmpty()
        return if (uid.isBlank()) "chat:$roomId" else "chat:$uid:$roomId"
    }

    fun forumScopeKey(teamId: String, topicId: String): String {
        val uid = activeUserId?.trim().orEmpty()
        return if (uid.isBlank()) {
            "forum:$teamId:$topicId"
        } else {
            "forum:$uid:$teamId:$topicId"
        }
    }

    fun load(scopeKey: String): List<PinnedMessagePreviewDto> {
        if (scopeKey.isBlank()) return emptyList()
        val raw = prefs.getString(storageKey(scopeKey), null) ?: return emptyList()
        return runCatching {
            adapter.fromJson(raw)?.entries.orEmpty().take(PIN_HISTORY_MAX)
        }.getOrDefault(emptyList())
    }

    fun save(scopeKey: String, history: List<PinnedMessagePreviewDto>) {
        if (scopeKey.isBlank()) return
        val trimmed = history.take(PIN_HISTORY_MAX)
        val json = adapter.toJson(CachedPinHistory(trimmed))
        prefs.edit().putString(storageKey(scopeKey), json).apply()
    }

    fun clearUser() {
        activeUserId = null
        val editor = prefs.edit()
        prefs.all.keys
            .filterIsInstance<String>()
            .filter { it.startsWith(KEY_PREFIX) || it.startsWith(DISMISS_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    fun isPinBarDismissed(scopeKey: String, activePinId: String): Boolean {
        val pin = activePinId.trim()
        if (scopeKey.isBlank() || pin.isEmpty()) return false
        return prefs.getString(dismissStorageKey(scopeKey), null)?.trim() == pin
    }

    fun setDismissedPinBar(scopeKey: String, activePinId: String) {
        val pin = activePinId.trim()
        if (scopeKey.isBlank() || pin.isEmpty()) return
        prefs.edit().putString(dismissStorageKey(scopeKey), pin).apply()
    }

    fun clearDismissedPinBar(scopeKey: String) {
        if (scopeKey.isBlank()) return
        prefs.edit().remove(dismissStorageKey(scopeKey)).apply()
    }

    private fun storageKey(scopeKey: String): String = KEY_PREFIX + scopeKey

    private fun dismissStorageKey(scopeKey: String): String = DISMISS_PREFIX + scopeKey

    private companion object {
        const val PREFS_NAME = "squadrelay_pin_history"
        const val KEY_PREFIX = "pin_hist_"
        const val DISMISS_PREFIX = "pin_dismiss_"
        const val PIN_HISTORY_MAX = 15
    }
}
