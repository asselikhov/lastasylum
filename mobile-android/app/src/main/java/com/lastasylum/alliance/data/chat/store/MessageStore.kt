package com.lastasylum.alliance.data.chat.store

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.ui.chat.dedupeMessagesByIdNewestFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MessageStore(
    private val db: SquadRelayDatabase,
) {
    private val roomDao get() = db.chatRoomDao()
    private val messageDao get() = db.chatMessageDao()
    private val cursorDao get() = db.chatReadCursorDao()
    private val tombstoneDao get() = db.chatTombstoneDao()

    fun observeMessages(userId: String, roomId: String): Flow<List<ChatMessage>> =
        messageDao.observeRoom(userId, roomId.trim()).map { rows ->
            rows.mapNotNull { ChatStoreJson.messageFromJson(it.payloadJson) }
        }

    fun observeRooms(userId: String): Flow<List<ChatRoomDto>> =
        roomDao.observeByUser(userId).map { rows ->
            rows.mapNotNull { ChatStoreJson.roomFromJson(it.payloadJson) }
        }

    fun observeReadCursor(userId: String, roomId: String): Flow<String?> =
        cursorDao.observe(userId, roomId.trim()).map { it?.lastReadMessageId }

    suspend fun getMessages(userId: String, roomId: String): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            messageDao.getRoom(userId, roomId.trim())
                .mapNotNull { ChatStoreJson.messageFromJson(it.payloadJson) }
        }

    suspend fun getHasMoreOlderHint(userId: String, roomId: String): Boolean =
        withContext(Dispatchers.IO) {
            messageDao.getRoom(userId, roomId.trim()).any { it.hasMoreOlderHint }
        }

    suspend fun getRooms(userId: String): List<ChatRoomDto> =
        withContext(Dispatchers.IO) {
            roomDao.getByUser(userId).mapNotNull { ChatStoreJson.roomFromJson(it.payloadJson) }
        }

    /** True when the room row exists in Room (even with zero messages). */
    suspend fun isRoomKnown(userId: String, roomId: String): Boolean =
        withContext(Dispatchers.IO) {
            val rid = roomId.trim()
            userId.isNotBlank() && rid.isNotEmpty() &&
                roomDao.getByUser(userId).any { it.roomId == rid }
        }

    suspend fun upsertMessages(
        userId: String,
        roomId: String,
        messages: List<ChatMessage>,
        hasMoreOlder: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val rid = roomId.trim()
        if (userId.isBlank() || rid.isEmpty() || messages.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val entities = dedupeMessagesByIdNewestFirst(messages).mapNotNull { msg ->
            val id = msg._id?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            ChatMessageEntity(
                roomId = rid,
                messageId = id,
                userId = userId,
                payloadJson = ChatStoreJson.messageToJson(msg),
                createdAtMs = ChatStoreJson.messageCreatedAtMs(msg),
                isDeleted = !msg.deletedAt.isNullOrBlank(),
                hasMoreOlderHint = hasMoreOlder,
            )
        }
        if (entities.isNotEmpty()) {
            messageDao.upsertAll(entities)
        }
        // Touch room sync timestamp when messages change.
        roomDao.getByUser(userId).firstOrNull { it.roomId == rid }?.let { existing ->
            roomDao.upsertAll(listOf(existing.copy(syncedAtMs = now)))
        }
    }

    suspend fun upsertRooms(userId: String, rooms: List<ChatRoomDto>) =
        withContext(Dispatchers.IO) {
            if (userId.isBlank() || rooms.isEmpty()) return@withContext
            val now = System.currentTimeMillis()
            roomDao.upsertAll(
                rooms.mapNotNull { room ->
                    val id = room.id.trim()
                    if (id.isEmpty()) return@mapNotNull null
                    ChatRoomEntity(
                        roomId = id,
                        userId = userId,
                        payloadJson = ChatStoreJson.roomToJson(room),
                        syncedAtMs = now,
                    )
                },
            )
        }

    suspend fun setReadCursor(userId: String, roomId: String, messageId: String) =
        withContext(Dispatchers.IO) {
            val rid = roomId.trim()
            val mid = messageId.trim()
            if (userId.isBlank() || rid.isEmpty() || mid.isEmpty()) return@withContext
            val existing = cursorDao.getAllForUser(userId).firstOrNull { it.roomId == rid }
            if (existing != null && !isObjectIdNewer(mid, existing.lastReadMessageId)) {
                return@withContext
            }
            cursorDao.upsert(
                ChatReadCursorEntity(
                    roomId = rid,
                    userId = userId,
                    lastReadMessageId = mid,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }

    suspend fun markMessageDeleted(messageId: String) = withContext(Dispatchers.IO) {
        val id = messageId.trim()
        if (id.isEmpty()) return@withContext
        messageDao.markDeleted(id)
    }

    suspend fun addTombstone(userId: String, messageId: String) = withContext(Dispatchers.IO) {
        val id = messageId.trim()
        if (userId.isBlank() || id.isEmpty()) return@withContext
        tombstoneDao.upsert(
            ChatTombstoneEntity(
                messageId = id,
                userId = userId,
                removedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun loadTombstones(userId: String): Set<String> = withContext(Dispatchers.IO) {
        tombstoneDao.getAllForUser(userId).toSet()
    }

    suspend fun clearUser(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        messageDao.deleteForUser(userId)
        roomDao.deleteForUser(userId)
        tombstoneDao.deleteForUser(userId)
    }

    suspend fun clearRoomMessages(userId: String, roomId: String) = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        val rid = roomId.trim()
        if (uid.isEmpty() || rid.isEmpty()) return@withContext
        messageDao.deleteForRoom(uid, rid)
    }
}
