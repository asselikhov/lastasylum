package com.lastasylum.alliance.data.teams

import android.content.Context

/** Persisted forum topic read cursors (per user, team, topic). */
class TeamForumPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var activeUserId: String? = null

    fun bindUser(userId: String) {
        val id = userId.trim()
        activeUserId = id.takeIf { it.isNotEmpty() }
    }

    fun getLastReadMessageId(teamId: String, topicId: String): String? =
        prefs.getString(key(teamId, topicId), null)

    fun setLastReadMessageId(teamId: String, topicId: String, messageId: String) {
        if (teamId.isBlank() || topicId.isBlank() || messageId.isBlank()) return
        prefs.edit().putString(key(teamId, topicId), messageId).apply()
    }

    fun loadAllLastReadMessageIds(teamId: String): Map<String, String> {
        if (teamId.isBlank()) return emptyMap()
        val prefix = keyPrefix(teamId)
        return prefs.all
            .mapNotNull { (k, v) ->
                if (k is String && k.startsWith(prefix) && v is String && v.isNotBlank()) {
                    k.removePrefix(prefix) to v
                } else {
                    null
                }
            }
            .toMap()
    }

    fun clear() {
        activeUserId = null
        val editor = prefs.edit()
        prefs.all.keys.filter { it is String && it.startsWith(KEY_PREFIX) }
            .forEach { editor.remove(it as String) }
        editor.apply()
    }

    private fun keyPrefix(teamId: String): String {
        val uid = activeUserId?.trim().orEmpty()
        return if (uid.isBlank()) {
            "$KEY_PREFIX$teamId:"
        } else {
            "$KEY_PREFIX$uid:$teamId:"
        }
    }

    private fun key(teamId: String, topicId: String): String = keyPrefix(teamId) + topicId

    private companion object {
        const val PREFS_NAME = "squadrelay_team_forum"
        const val KEY_PREFIX = "forum_last_read_"
    }
}
