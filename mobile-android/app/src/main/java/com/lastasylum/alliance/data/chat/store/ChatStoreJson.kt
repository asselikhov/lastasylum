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

    /**
     * Ключ сортировки в БД (`createdAtMs`, ORDER BY DESC). Должен совпадать с in-memory
     * `sortMessagesNewestFirst`, иначе первый кадр из DAO визуально пересортировывается после
     * REST-рефреша. Поэтому: лёгкий разбор ISO (с зоной и без — без зоны считаем UTC), затем
     * timestamp из ObjectId, и только потом стабильный минимум (а НЕ write-time now, который
     * раньше «всплывал» нераспознанные метки в голову ленты).
     */
    fun messageCreatedAtMs(message: ChatMessage): Long {
        parseIsoMs(message.createdAt)?.let { return it }
        objectIdTimestampMs(message._id)?.let { return it }
        return Long.MIN_VALUE
    }

    fun forumMessageCreatedAtMs(message: TeamForumMessageDto): Long =
        parseIsoMs(message.createdAt) ?: System.currentTimeMillis()

    /** Лёгкий разбор ISO: `...Z`, offset (`+03:00`) и форма без зоны (трактуем как UTC). */
    private fun parseIsoMs(iso: String?): Long? {
        val s = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching {
            java.time.OffsetDateTime
                .parse(s, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()?.let { return it }
        runCatching {
            java.time.LocalDateTime
                .parse(s, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()?.let { return it }
        return null
    }

    /** Время вставки из Mongo ObjectId (первые 8 hex-символов = unix-секунды). */
    private fun objectIdTimestampMs(id: String?): Long? {
        val trimmed = id?.trim() ?: return null
        if (trimmed.length != 24) return null
        return runCatching { trimmed.substring(0, 8).toLong(16) * 1000L }.getOrNull()
    }
}
