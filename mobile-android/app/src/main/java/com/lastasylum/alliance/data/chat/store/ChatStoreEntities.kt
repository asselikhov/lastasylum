package com.lastasylum.alliance.data.chat.store

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_rooms")
data class ChatRoomEntity(
    @PrimaryKey val roomId: String,
    val userId: String,
    val payloadJson: String,
    val syncedAtMs: Long,
)

@Entity(
    tableName = "chat_messages",
    primaryKeys = ["roomId", "messageId"],
    indices = [Index("roomId"), Index("createdAtMs")],
)
data class ChatMessageEntity(
    val roomId: String,
    val messageId: String,
    val userId: String,
    val payloadJson: String,
    val createdAtMs: Long,
    val isDeleted: Boolean = false,
    val hasMoreOlderHint: Boolean = false,
)

@Entity(tableName = "chat_read_cursors")
data class ChatReadCursorEntity(
    @PrimaryKey val roomId: String,
    val userId: String,
    val lastReadMessageId: String,
    val updatedAtMs: Long,
)

@Entity(tableName = "chat_tombstones")
data class ChatTombstoneEntity(
    @PrimaryKey val messageId: String,
    val userId: String,
    val removedAtMs: Long,
)

@Entity(
    tableName = "chat_outbox",
    indices = [Index("roomId"), Index("state")],
)
data class ChatOutboxEntity(
    @PrimaryKey val clientMessageId: String,
    val userId: String,
    val roomId: String,
    val pendingMessageId: String,
    val text: String,
    val replyToMessageId: String?,
    val attachmentsJson: String?,
    val excavationAlert: Boolean,
    val gameEventAlert: String? = null,
    val source: String,
    val state: String,
    val attempts: Int,
    val createdAtMs: Long,
    val lastError: String? = null,
)

@Entity(
    tableName = "forum_topics",
    primaryKeys = ["teamId", "topicId"],
    indices = [Index("teamId")],
)
data class ForumTopicEntity(
    val teamId: String,
    val topicId: String,
    val userId: String,
    val payloadJson: String,
    val syncedAtMs: Long,
)

@Entity(
    tableName = "forum_messages",
    primaryKeys = ["teamId", "topicId", "messageId"],
    indices = [Index(value = ["teamId", "topicId"])],
)
data class ForumMessageEntity(
    val teamId: String,
    val topicId: String,
    val messageId: String,
    val userId: String,
    val payloadJson: String,
    val createdAtMs: Long,
)

@Entity(
    tableName = "forum_read_cursors",
    primaryKeys = ["teamId", "topicId"],
)
data class ForumReadCursorEntity(
    val teamId: String,
    val topicId: String,
    val userId: String,
    val lastReadMessageId: String,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "forum_outbox",
    indices = [Index(value = ["teamId", "topicId"]), Index("state")],
)
data class ForumOutboxEntity(
    @PrimaryKey val clientMessageId: String,
    val userId: String,
    val teamId: String,
    val topicId: String,
    val pendingMessageId: String,
    val text: String,
    val replyToMessageId: String?,
    val imageFileIdsJson: String?,
    val fileFileId: String?,
    val state: String,
    val attempts: Int,
    val createdAtMs: Long,
    val lastError: String? = null,
)

@Entity(
    tableName = "latency_samples",
    indices = [Index("spanType"), Index("startedAtMs")],
)
data class LatencySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spanType: String,
    val correlationId: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val outcome: String,
)
