package com.lastasylum.alliance.data.teams

/**
 * Buffers forum `message:new` events received on personal fanout while the client
 * is on team inbox (not subscribed to a specific topic room).
 */
object ForumMessageStash {
    const val MAX_PER_TOPIC = 64

    private val lock = Any()
    private val byKey = mutableMapOf<String, MutableList<TeamForumMessageDto>>()
    private var overflowListener: ((teamId: String, topicId: String) -> Unit)? = null

    fun setOverflowListener(listener: ((teamId: String, topicId: String) -> Unit)?) {
        synchronized(lock) {
            overflowListener = listener
        }
    }

    private fun key(teamId: String, topicId: String): String =
        "${teamId.trim()}|${topicId.trim()}"

    fun stash(message: TeamForumMessageDto): Boolean {
        val teamId = message.teamId.trim()
        val topicId = message.topicId.trim()
        if (teamId.isEmpty() || topicId.isEmpty()) return false
        val k = key(teamId, topicId)
        var overflow = false
        synchronized(lock) {
            val list = byKey.getOrPut(k) { mutableListOf() }
            if (list.any { it.id == message.id }) return false
            val clientId = message.clientMessageId?.trim().orEmpty()
            if (clientId.isNotEmpty() && list.any { it.clientMessageId?.trim() == clientId }) return false
            list.add(message)
            if (list.size > MAX_PER_TOPIC) {
                list.removeAt(0)
                overflow = true
            }
        }
        if (overflow) {
            overflowListener?.invoke(teamId, topicId)
        }
        return true
    }

    fun drain(teamId: String, topicId: String): List<TeamForumMessageDto> {
        val k = key(teamId, topicId)
        synchronized(lock) {
            return byKey.remove(k)?.toList().orEmpty()
        }
    }
}
