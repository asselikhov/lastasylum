package com.lastasylum.alliance.data.teams

import android.content.Context

/** Persisted forum topic read cursors (per team+topic). */
class TeamForumPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastReadMessageId(teamId: String, topicId: String): String? =
        prefs.getString(key(teamId, topicId), null)

    fun setLastReadMessageId(teamId: String, topicId: String, messageId: String) {
        if (teamId.isBlank() || topicId.isBlank() || messageId.isBlank()) return
        prefs.edit().putString(key(teamId, topicId), messageId).apply()
    }

    fun loadAllLastReadMessageIds(teamId: String): Map<String, String> {
        if (teamId.isBlank()) return emptyMap()
        val prefix = "$KEY_PREFIX$teamId:"
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

    private fun key(teamId: String, topicId: String): String = "$KEY_PREFIX$teamId:$topicId"

    private companion object {
        const val PREFS_NAME = "squadrelay_team_forum"
        const val KEY_PREFIX = "forum_last_read_"
    }
}
