package com.lastasylum.alliance.data.teams.forum

import com.lastasylum.alliance.data.chat.store.ForumOutboxEntity
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class ForumOutboxEntry(
    val clientMessageId: String,
    val userId: String,
    val teamId: String,
    val topicId: String,
    val pendingMessageId: String,
    val text: String,
    val replyToMessageId: String?,
    val imageFileIds: List<String>?,
    val fileFileId: String?,
    val state: String,
    val attempts: Int,
    val createdAtMs: Long,
    val lastError: String?,
)

class ForumOutbox(
    private val db: SquadRelayDatabase,
) {
    private val dao get() = db.forumOutboxDao()
    private val resumeMutex = Mutex()

    suspend fun persist(entry: ForumOutboxEntry) = withContext(Dispatchers.IO) {
        dao.upsert(entry.toEntity())
    }

    suspend fun markSent(clientMessageId: String) = withContext(Dispatchers.IO) {
        dao.delete(clientMessageId.trim())
    }

    suspend fun markFailed(clientMessageId: String, error: String) = withContext(Dispatchers.IO) {
        val row = dao.getByClientId(clientMessageId.trim()) ?: return@withContext
        dao.upsert(
            row.copy(
                state = "failed",
                attempts = row.attempts + 1,
                lastError = error.take(512),
            ),
        )
    }

    suspend fun sendOutboxEntry(
        entry: ForumOutboxEntry,
        sendBlock: suspend (ForumOutboxEntry) -> Result<TeamForumMessageDto>,
    ): Result<TeamForumMessageDto> {
        val result = sendBlock(entry)
        result.onSuccess { sent ->
            markSent(entry.clientMessageId)
            ForumOutboxUiBridge.onSendSuccess(entry, sent)
        }.onFailure { err ->
            markFailed(entry.clientMessageId, err.message ?: "send_failed")
        }
        return result
    }

    suspend fun resumePendingSync(
        userId: String,
        sendBlock: suspend (ForumOutboxEntry) -> Result<TeamForumMessageDto>,
    ) = resumeMutex.withLock {
        val uid = userId.trim()
        if (uid.isEmpty()) return@withLock
        withContext(Dispatchers.IO) { dao.recoverStuckSendingToFailed(uid) }
        val pending = withContext(Dispatchers.IO) { dao.getResumable(uid) }
        pending.forEach { row ->
            val entry = row.toEntry()
            if (withContext(Dispatchers.IO) { dao.tryMarkSending(entry.clientMessageId) } <= 0) {
                return@forEach
            }
            sendOutboxEntry(entry, sendBlock)
        }
    }

    private fun ForumOutboxEntry.toEntity(): ForumOutboxEntity = ForumOutboxEntity(
        clientMessageId = clientMessageId,
        userId = userId,
        teamId = teamId,
        topicId = topicId,
        pendingMessageId = pendingMessageId,
        text = text,
        replyToMessageId = replyToMessageId,
        imageFileIdsJson = imageFileIds?.takeIf { it.isNotEmpty() }?.let { JSONArray(it).toString() },
        fileFileId = fileFileId,
        state = state,
        attempts = attempts,
        createdAtMs = createdAtMs,
        lastError = lastError,
    )

    private fun ForumOutboxEntity.toEntry(): ForumOutboxEntry {
        val imageIds = imageFileIdsJson?.trim()?.takeIf { it.isNotEmpty() }?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }.getOrNull()
        }
        return ForumOutboxEntry(
            clientMessageId = clientMessageId,
            userId = userId,
            teamId = teamId,
            topicId = topicId,
            pendingMessageId = pendingMessageId,
            text = text,
            replyToMessageId = replyToMessageId,
            imageFileIds = imageIds,
            fileFileId = fileFileId,
            state = state,
            attempts = attempts,
            createdAtMs = createdAtMs,
            lastError = lastError,
        )
    }
}
