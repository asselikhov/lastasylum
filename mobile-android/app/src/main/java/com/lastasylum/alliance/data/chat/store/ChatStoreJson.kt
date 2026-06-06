package com.lastasylum.alliance.data.chat.store

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.SquadRelayMoshi
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

internal object ChatStoreJson {
    private val moshi: Moshi = SquadRelayMoshi.build()

    private val messageAdapter = moshi.adapter(ChatMessage::class.java)
    private val roomAdapter = moshi.adapter(ChatRoomDto::class.java)
    private val forumTopicAdapter = moshi.adapter(TeamForumTopicDto::class.java)
    private val forumMessageAdapter = moshi.adapter(TeamForumMessageDto::class.java)
    private val stringListAdapter =
        moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))

    fun messageToJson(message: ChatMessage): String = messageAdapter.toJson(message)

    fun messageFromJson(json: String): ChatMessage? =
        runCatching { messageAdapter.fromJson(json) }.getOrNull()

    fun roomToJson(room: ChatRoomDto): String = roomAdapter.toJson(room)

    fun roomFromJson(json: String): ChatRoomDto? =
        runCatching { roomAdapter.fromJson(json) }.getOrNull()

    fun forumTopicToJson(topic: TeamForumTopicDto): String = forumTopicAdapter.toJson(topic)

    fun forumTopicFromJson(json: String): TeamForumTopicDto? =
        runCatching { forumTopicAdapter.fromJson(json) }.getOrNull()

    fun forumMessageToJson(message: TeamForumMessageDto): String = forumMessageAdapter.toJson(message)

    fun forumMessageFromJson(json: String): TeamForumMessageDto? =
        runCatching { forumMessageAdapter.fromJson(json) }.getOrNull()

    fun attachmentsToJson(attachments: List<String>?): String? =
        attachments?.let { stringListAdapter.toJson(it) }

    fun attachmentsFromJson(json: String?): List<String>? =
        json?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { stringListAdapter.fromJson(it) }.getOrNull()
        }

    fun messageCreatedAtMs(message: ChatMessage): Long =
        message.createdAt?.let { parseIsoMs(it) } ?: System.currentTimeMillis()

    fun forumMessageCreatedAtMs(message: TeamForumMessageDto): Long =
        message.createdAt?.let { parseIsoMs(it) } ?: System.currentTimeMillis()

    private fun parseIsoMs(iso: String): Long? =
        runCatching { java.time.Instant.parse(iso.trim()).toEpochMilli() }.getOrNull()
}
