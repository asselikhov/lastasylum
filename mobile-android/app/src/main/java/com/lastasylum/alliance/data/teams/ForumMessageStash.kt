package com.lastasylum.alliance.data.teams

/**
 * Buffers forum `message:new` events received on personal fanout while the client
 * is on team inbox (not subscribed to a specific topic room).
 */
object ForumMessageStash {
    private const val MAX_PER_TOPIC = 64
    private val lock = Any()
    private val byKey = mutableMapOf<String, MutableList<TeamForumMessageDto>>()

    private fun key(teamId: String, topicId: String): String =
        "${teamId.trim()}|${topicId.trim()}"

    fun stash(message: TeamForumMessageDto) {
        val teamId = message.teamId.trim()
        val topicId = message.topicId.trim()
        if (teamId.isEmpty() || topicId.isEmpty()) return
        val k = key(teamId, topicId)
        synchronized(lock) {
            val list = byKey.getOrPut(k) { mutableListOf() }
            if (list.any { it.id == message.id }) return
            val clientId = message.clientMessageId?.trim().orEmpty()
            if (clientId.isNotEmpty() && list.any { it.clientMessageId?.trim() == clientId }) return
            list.add(message)
            while (list.size > MAX_PER_TOPIC) {
                list.removeAt(0)
            }
        }
    }

    fun drain(teamId: String, topicId: String): List<TeamForumMessageDto> {
        val k = key(teamId, topicId)
        synchronized(lock) {
            return byKey.remove(k)?.toList().orEmpty()
        }
    }
}
