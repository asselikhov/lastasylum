package com.lastasylum.alliance.data.chat.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatRoomDao {
    @Query("SELECT * FROM chat_rooms WHERE userId = :userId ORDER BY syncedAtMs DESC")
    fun observeByUser(userId: String): Flow<List<ChatRoomEntity>>

    @Query("SELECT * FROM chat_rooms WHERE userId = :userId")
    suspend fun getByUser(userId: String): List<ChatRoomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rooms: List<ChatRoomEntity>)

    @Query("DELETE FROM chat_rooms WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface ChatMessageDao {
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE userId = :userId AND roomId = :roomId AND isDeleted = 0
        ORDER BY createdAtMs DESC
        """,
    )
    fun observeRoom(userId: String, roomId: String): Flow<List<ChatMessageEntity>>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE userId = :userId AND roomId = :roomId AND isDeleted = 0
        ORDER BY createdAtMs DESC
        """,
    )
    suspend fun getRoom(userId: String, roomId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<ChatMessageEntity>)

    @Query("UPDATE chat_messages SET isDeleted = 1 WHERE messageId = :messageId")
    suspend fun markDeleted(messageId: String)

    @Query("DELETE FROM chat_messages WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)

    @Query("DELETE FROM chat_messages WHERE userId = :userId AND roomId = :roomId")
    suspend fun deleteForRoom(userId: String, roomId: String)
}

@Dao
interface ChatReadCursorDao {
    @Query("SELECT * FROM chat_read_cursors WHERE userId = :userId AND roomId = :roomId LIMIT 1")
    fun observe(userId: String, roomId: String): Flow<ChatReadCursorEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: ChatReadCursorEntity)

    @Query("SELECT * FROM chat_read_cursors WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<ChatReadCursorEntity>
}

@Dao
interface ChatTombstoneDao {
    @Query("SELECT messageId FROM chat_tombstones WHERE userId = :userId")
    suspend fun getAllForUser(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatTombstoneEntity)

    @Query("DELETE FROM chat_tombstones WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface ChatOutboxDao {
    @Query("SELECT * FROM chat_outbox WHERE userId = :userId AND state IN ('pending','sending','failed')")
    fun observeActive(userId: String): Flow<List<ChatOutboxEntity>>

    @Query("SELECT * FROM chat_outbox WHERE userId = :userId AND roomId = :roomId AND state IN ('pending','sending','failed')")
    fun observeActiveForRoom(userId: String, roomId: String): Flow<List<ChatOutboxEntity>>

    @Query("SELECT * FROM chat_outbox WHERE userId = :userId AND state IN ('pending','failed') ORDER BY createdAtMs ASC")
    suspend fun getResumable(userId: String): List<ChatOutboxEntity>

    @Query("SELECT * FROM chat_outbox WHERE clientMessageId = :clientMessageId LIMIT 1")
    suspend fun getByClientId(clientMessageId: String): ChatOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ChatOutboxEntity)

    @Query("DELETE FROM chat_outbox WHERE clientMessageId = :clientMessageId")
    suspend fun delete(clientMessageId: String)

    @Query("DELETE FROM chat_outbox WHERE userId = :userId AND state = 'sent'")
    suspend fun pruneSent(userId: String)

    @Query("DELETE FROM chat_outbox WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)

    @Query(
        """
        UPDATE chat_outbox SET state = 'sending'
        WHERE clientMessageId = :clientMessageId AND state IN ('pending','failed')
        """,
    )
    suspend fun tryMarkSending(clientMessageId: String): Int
}

@Dao
interface ForumTopicDao {
    @Query("SELECT * FROM forum_topics WHERE userId = :userId AND teamId = :teamId ORDER BY syncedAtMs DESC")
    fun observeTeam(userId: String, teamId: String): Flow<List<ForumTopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(topics: List<ForumTopicEntity>)

    @Query(
        """
        SELECT * FROM forum_topics
        WHERE userId = :userId AND teamId = :teamId AND topicId = :topicId
        LIMIT 1
        """,
    )
    suspend fun getTopic(userId: String, teamId: String, topicId: String): ForumTopicEntity?

    @Query("DELETE FROM forum_topics WHERE userId = :userId AND teamId = :teamId")
    suspend fun deleteTeam(userId: String, teamId: String)
}

@Dao
interface ForumMessageDao {
    @Query(
        """
        SELECT * FROM forum_messages
        WHERE userId = :userId AND teamId = :teamId AND topicId = :topicId
        ORDER BY createdAtMs ASC
        """,
    )
    fun observeTopic(userId: String, teamId: String, topicId: String): Flow<List<ForumMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<ForumMessageEntity>)
}

@Dao
interface ForumReadCursorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: ForumReadCursorEntity)

    @Query("SELECT * FROM forum_read_cursors WHERE userId = :userId AND teamId = :teamId")
    suspend fun getForTeam(userId: String, teamId: String): List<ForumReadCursorEntity>
}

@Dao
interface ForumOutboxDao {
    @Query("SELECT * FROM forum_outbox WHERE userId = :userId AND state IN ('pending','sending','failed') ORDER BY createdAtMs ASC")
    suspend fun getResumable(userId: String): List<ForumOutboxEntity>

    @Query("SELECT * FROM forum_outbox WHERE clientMessageId = :clientMessageId LIMIT 1")
    suspend fun getByClientId(clientMessageId: String): ForumOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ForumOutboxEntity)

    @Query("DELETE FROM forum_outbox WHERE clientMessageId = :clientMessageId")
    suspend fun delete(clientMessageId: String)

    @Query(
        """
        UPDATE forum_outbox SET state = 'sending'
        WHERE clientMessageId = :clientMessageId AND state IN ('pending','failed')
        """,
    )
    suspend fun tryMarkSending(clientMessageId: String): Int

    @Query("DELETE FROM forum_outbox WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface LatencySampleDao {
    @Insert
    suspend fun insert(sample: LatencySampleEntity)

    @Query("SELECT * FROM latency_samples WHERE startedAtMs >= :sinceMs ORDER BY startedAtMs DESC LIMIT :limit")
    suspend fun since(sinceMs: Long, limit: Int = 500): List<LatencySampleEntity>

    @Query("DELETE FROM latency_samples WHERE startedAtMs < :beforeMs")
    suspend fun pruneBefore(beforeMs: Long)
}
