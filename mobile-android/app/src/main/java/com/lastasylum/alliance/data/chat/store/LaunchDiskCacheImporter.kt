package com.lastasylum.alliance.data.chat.store

import android.content.Context
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-time import from legacy JSON cache + SharedPreferences read cursors into Room.
 */
class LaunchDiskCacheImporter(
    private val context: Context,
    private val launchDiskCache: LaunchDiskCache,
    private val chatRoomPreferences: ChatRoomPreferences,
    private val messageStore: MessageStore,
) {
    suspend fun importIfNeeded(userId: String) = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        if (uid.isEmpty()) return@withContext
        if (MigrationFlags.isChatDiskImported(context)) return@withContext

        launchDiskCache.loadChatRooms(uid)?.let { rooms ->
            messageStore.upsertRooms(uid, rooms)
        }

        val userDir = File(context.filesDir, "launch_cache/$uid")
        if (userDir.isDirectory) {
            userDir.listFiles()?.forEach { file ->
                val name = file.name
                if (!name.startsWith("messages_") || !name.endsWith(".json")) return@forEach
                val roomId = name.removePrefix("messages_").removeSuffix(".json")
                launchDiskCache.loadRoomMessages(uid, roomId)?.let { cached ->
                    messageStore.upsertMessages(
                        userId = uid,
                        roomId = roomId,
                        messages = cached.messages,
                        hasMoreOlder = cached.hasMoreOlder,
                    )
                }
            }
        }

        chatRoomPreferences.loadAllLastReadMessageIds().forEach { (roomId, lastRead) ->
            messageStore.setReadCursor(uid, roomId, lastRead)
        }

        launchDiskCache.loadRemovedMessageIds(uid)?.forEach { id ->
            messageStore.addTombstone(uid, id)
        }

        MigrationFlags.markChatDiskImported(context)
    }

    suspend fun importForumIfNeeded(userId: String, teamId: String) = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        val tid = teamId.trim()
        if (uid.isEmpty() || tid.isEmpty()) return@withContext
        if (MigrationFlags.isForumDiskImported(context)) return@withContext

        val topics = launchDiskCache.loadForumTopics(uid, tid) ?: return@withContext
        val now = System.currentTimeMillis()
        SquadRelayDatabase.get(context).forumTopicDao().upsertAll(
            topics.map { topic ->
                ForumTopicEntity(
                    teamId = tid,
                    topicId = topic.id,
                    userId = uid,
                    payloadJson = ChatStoreJson.forumTopicToJson(topic),
                    syncedAtMs = now,
                )
            },
        )

        MigrationFlags.markForumDiskImported(context)
    }
}
