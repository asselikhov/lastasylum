package com.lastasylum.alliance.data

private val OBJECT_ID_HEX = Regex("^[a-fA-F0-9]{24}$")

/** Helpers for legacy vs user-scoped read-cursor SharedPreferences keys. */
object ReadCursorPrefKeys {
    const val CHAT_PREFIX = "last_read_msg_"
    const val FORUM_PREFIX = "forum_last_read_"

    fun isLegacyChatReadKey(key: String): Boolean {
        if (!key.startsWith(CHAT_PREFIX)) return false
        val suffix = key.removePrefix(CHAT_PREFIX)
        return suffix.isNotEmpty() && !suffix.contains(':') && OBJECT_ID_HEX.matches(suffix)
    }

    fun chatReadKey(userId: String, roomId: String): String =
        "$CHAT_PREFIX${userId.trim()}:$roomId"

    fun isLegacyForumReadKey(key: String): Boolean {
        if (!key.startsWith(FORUM_PREFIX)) return false
        val suffix = key.removePrefix(FORUM_PREFIX)
        if (suffix.count { it == ':' } != 1) return false
        val parts = suffix.split(':', limit = 2)
        return parts.size == 2 &&
            OBJECT_ID_HEX.matches(parts[0]) &&
            OBJECT_ID_HEX.matches(parts[1])
    }

    fun parseLegacyForumReadKey(key: String): Pair<String, String>? {
        if (!isLegacyForumReadKey(key)) return null
        val suffix = key.removePrefix(FORUM_PREFIX)
        val parts = suffix.split(':', limit = 2)
        return parts[0] to parts[1]
    }

    fun forumReadKey(userId: String, teamId: String, topicId: String): String =
        "$FORUM_PREFIX${userId.trim()}:$teamId:$topicId"

    fun mergeReadMessageIds(existing: String?, incoming: String): String {
        val prev = existing?.trim().orEmpty()
        return when {
            prev.isBlank() -> incoming
            isObjectIdNewer(incoming, prev) -> incoming
            else -> prev
        }
    }
}
