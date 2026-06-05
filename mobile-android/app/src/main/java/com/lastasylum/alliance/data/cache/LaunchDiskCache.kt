package com.lastasylum.alliance.data.cache

import android.content.Context
import com.lastasylum.alliance.data.auth.AuthUser
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamOverlayPresenceDto
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamNewsListPageDto
import com.lastasylum.alliance.data.users.MyProfileDto
import com.squareup.moshi.JsonClass
import com.lastasylum.alliance.data.chat.SquadRelayMoshi
import com.squareup.moshi.Moshi
import java.io.File

/**
 * Per-user JSON snapshots for offline-first launch (survives process death, not APK reinstall).
 */
class LaunchDiskCache(private val context: Context) {

    private val moshi: Moshi = SquadRelayMoshi.build()

    private val profileAdapter = moshi.adapter(CachedProfile::class.java)
    private val teamAdapter = moshi.adapter(CachedTeam::class.java)
    private val roomsAdapter = moshi.adapter(CachedChatRooms::class.java)
    private val messagesAdapter = moshi.adapter(CachedRoomMessages::class.java)
    private val removedMessageIdsAdapter = moshi.adapter(CachedRemovedMessageIds::class.java)
    private val newsAdapter = moshi.adapter(CachedTeamNews::class.java)
    private val forumTopicsAdapter = moshi.adapter(CachedForumTopics::class.java)
    private val forumMessagesAdapter = moshi.adapter(CachedForumMessages::class.java)
    private val overlayPresenceAdapter = moshi.adapter(CachedOverlayPresence::class.java)
    private val authUserAdapter = moshi.adapter(CachedAuthUser::class.java)

    fun saveAuthUser(userId: String, user: AuthUser) {
        if (userId.isBlank()) return
        write(userId, FILE_AUTH_USER, authUserAdapter.toJson(CachedAuthUser(user, nowMs())))
    }

    fun loadAuthUser(userId: String): AuthUser? =
        readCached(userId, FILE_AUTH_USER, authUserAdapter)?.user

    fun saveProfile(userId: String, profile: MyProfileDto) {
        if (userId.isBlank()) return
        write(userId, FILE_PROFILE, profileAdapter.toJson(CachedProfile(profile, nowMs())))
    }

    fun loadProfile(userId: String): MyProfileDto? =
        readCached(userId, FILE_PROFILE, profileAdapter)?.profile

    fun saveTeam(userId: String, team: TeamDetailDto) {
        if (userId.isBlank()) return
        write(userId, FILE_TEAM, teamAdapter.toJson(CachedTeam(team, nowMs())))
    }

    fun loadTeam(userId: String): TeamDetailDto? =
        readCached(userId, FILE_TEAM, teamAdapter)?.team

    fun saveChatRooms(userId: String, rooms: List<ChatRoomDto>) {
        if (userId.isBlank() || rooms.isEmpty()) return
        write(userId, FILE_CHAT_ROOMS, roomsAdapter.toJson(CachedChatRooms(rooms, nowMs())))
    }

    fun loadChatRooms(userId: String): List<ChatRoomDto>? =
        readCached(userId, FILE_CHAT_ROOMS, roomsAdapter)?.rooms?.takeIf { it.isNotEmpty() }

    fun clearChatRooms(userId: String) {
        if (userId.isBlank()) return
        File(userDir(userId), FILE_CHAT_ROOMS).delete()
    }

    fun saveRoomMessages(
        userId: String,
        roomId: String,
        messages: List<ChatMessage>,
        hasMoreOlder: Boolean,
    ) {
        if (userId.isBlank() || roomId.isBlank() || messages.isEmpty()) return
        val fileName = messageFileName(roomId)
        write(
            userId,
            fileName,
            messagesAdapter.toJson(
                CachedRoomMessages(messages, hasMoreOlder, nowMs()),
            ),
        )
        trimMessageRoomFiles(userId, keepRoomId = roomId)
    }

    fun loadRoomMessages(userId: String, roomId: String): CachedRoomMessages? {
        if (userId.isBlank() || roomId.isBlank()) return null
        return readCached(userId, messageFileName(roomId), messagesAdapter)
    }

    fun loadRemovedMessageIds(userId: String): Set<String> {
        if (userId.isBlank()) return emptySet()
        return readCached(userId, FILE_REMOVED_MESSAGE_IDS, removedMessageIdsAdapter)
            ?.messageIds
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }

    fun saveRemovedMessageIds(userId: String, ids: Collection<String>) {
        if (userId.isBlank()) return
        val capped = ids
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .takeLast(MAX_REMOVED_MESSAGE_IDS)
        write(
            userId,
            FILE_REMOVED_MESSAGE_IDS,
            removedMessageIdsAdapter.toJson(CachedRemovedMessageIds(capped, nowMs())),
        )
    }

    fun addRemovedMessageId(userId: String, messageId: String) {
        val id = messageId.trim()
        if (userId.isBlank() || id.isEmpty()) return
        val merged = loadRemovedMessageIds(userId) + id
        saveRemovedMessageIds(userId, merged)
    }

    fun saveTeamNews(userId: String, teamId: String, page: TeamNewsListPageDto) {
        if (userId.isBlank() || teamId.isBlank()) return
        write(
            userId,
            newsFileName(teamId),
            newsAdapter.toJson(CachedTeamNews(page, nowMs())),
        )
    }

    fun loadTeamNews(userId: String, teamId: String): TeamNewsListPageDto? {
        val cached = readCached(userId, newsFileName(teamId), newsAdapter) ?: return null
        if (isStale(cached.savedAtMs)) return null
        return cached.page
    }

    fun saveForumTopics(userId: String, teamId: String, topics: List<TeamForumTopicDto>) {
        if (userId.isBlank() || teamId.isBlank()) return
        write(
            userId,
            forumFileName(teamId),
            forumTopicsAdapter.toJson(CachedForumTopics(topics, nowMs())),
        )
    }

    fun loadForumTopics(userId: String, teamId: String): List<TeamForumTopicDto>? {
        val cached = readCached(userId, forumFileName(teamId), forumTopicsAdapter) ?: return null
        if (isStale(cached.savedAtMs)) return null
        return cached.topics
    }

    fun saveForumMessages(
        userId: String,
        teamId: String,
        topicId: String,
        messages: List<TeamForumMessageDto>,
        hasMoreOlder: Boolean,
    ) {
        if (userId.isBlank() || teamId.isBlank() || topicId.isBlank()) return
        write(
            userId,
            forumMessagesFileName(teamId, topicId),
            forumMessagesAdapter.toJson(
                CachedForumMessages(messages, hasMoreOlder, nowMs()),
            ),
        )
        trimForumMessageFiles(userId, keepFileName = forumMessagesFileName(teamId, topicId))
    }

    fun saveOverlayPresence(
        userId: String,
        teamId: String,
        presence: TeamOverlayPresenceDto,
    ) {
        if (userId.isBlank() || teamId.isBlank()) return
        write(
            userId,
            overlayPresenceFileName(teamId),
            overlayPresenceAdapter.toJson(CachedOverlayPresence(presence, nowMs())),
        )
    }

    fun loadOverlayPresence(
        userId: String,
        teamId: String,
        maxAgeMs: Long = OVERLAY_PRESENCE_DISK_SOFT_TTL_MS,
    ): TeamOverlayPresenceDto? {
        if (userId.isBlank() || teamId.isBlank()) return null
        val cached = readCached(
            userId,
            overlayPresenceFileName(teamId),
            overlayPresenceAdapter,
        ) ?: return null
        if (maxAgeMs > 0L && System.currentTimeMillis() - cached.savedAtMs > maxAgeMs) {
            return null
        }
        return cached.presence
    }

    fun loadForumMessages(
        userId: String,
        teamId: String,
        topicId: String,
    ): CachedForumMessages? {
        if (userId.isBlank() || teamId.isBlank() || topicId.isBlank()) return null
        val cached = readCached(
            userId,
            forumMessagesFileName(teamId, topicId),
            forumMessagesAdapter,
        ) ?: return null
        if (isStale(cached.savedAtMs)) return null
        return cached
    }

    fun clearUser(userId: String) {
        if (userId.isBlank()) return
        userDir(userId).deleteRecursively()
    }

    /** Remove only chat history snapshots (rooms/messages), keep profile/team/news/forum. */
    fun clearChatHistory(userId: String) {
        if (userId.isBlank()) return
        val dir = userDir(userId)
        if (!dir.isDirectory) return
        // Messages per room.
        dir.listFiles { f ->
            f.isFile && f.name.startsWith(MESSAGE_FILE_PREFIX) && f.name.endsWith(".json")
        }?.forEach { it.delete() }
        // Rooms snapshot (unread counts/read cursors may be stale after admin wipe).
        File(dir, FILE_CHAT_ROOMS).delete()
        // Deleted-id filter is now meaningless; drop to avoid hiding new messages.
        File(dir, FILE_REMOVED_MESSAGE_IDS).delete()
    }

    fun clearAll() {
        cacheRoot().deleteRecursively()
    }

    private fun trimMessageRoomFiles(userId: String, keepRoomId: String) {
        val dir = userDir(userId)
        if (!dir.isDirectory) return
        val messageFiles = dir.listFiles { f ->
            f.isFile && f.name.startsWith(MESSAGE_FILE_PREFIX) && f.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() }.orEmpty()
        if (messageFiles.size <= MAX_MESSAGE_ROOM_FILES) return
        val keepName = messageFileName(keepRoomId)
        messageFiles
            .filter { it.name != keepName }
            .drop(MAX_MESSAGE_ROOM_FILES - 1)
            .forEach { it.delete() }
    }

    private fun <T> readCached(userId: String, fileName: String, adapter: com.squareup.moshi.JsonAdapter<T>): T? {
        val file = file(userId, fileName)
        if (!file.isFile) return null
        return runCatching {
            adapter.fromJson(file.readText())
        }.getOrNull()
    }

    private fun write(userId: String, fileName: String, json: String) {
        val file = file(userId, fileName)
        file.parentFile?.mkdirs()
        file.writeText(json)
    }

    private fun file(userId: String, fileName: String): File =
        File(userDir(userId), fileName)

    private fun userDir(userId: String): File =
        File(cacheRoot(), sanitizeUserId(userId))

    private fun cacheRoot(): File =
        File(context.filesDir, CACHE_DIR_NAME)

    private fun sanitizeUserId(userId: String): String =
        userId.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun messageFileName(roomId: String): String =
        "$MESSAGE_FILE_PREFIX${sanitizeUserId(roomId)}.json"

    private fun newsFileName(teamId: String): String =
        "news_${sanitizeUserId(teamId)}.json"

    private fun forumFileName(teamId: String): String =
        "forum_topics_${sanitizeUserId(teamId)}.json"

    private fun forumMessagesFileName(teamId: String, topicId: String): String =
        "forum_messages_${sanitizeUserId(teamId)}_${sanitizeUserId(topicId)}.json"

    private fun overlayPresenceFileName(teamId: String): String =
        "overlay_presence_${sanitizeUserId(teamId)}.json"

    private fun trimForumMessageFiles(userId: String, keepFileName: String) {
        val dir = userDir(userId)
        if (!dir.isDirectory) return
        val messageFiles = dir.listFiles { f ->
            f.isFile &&
                f.name.startsWith(FORUM_MESSAGES_FILE_PREFIX) &&
                f.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() }.orEmpty()
        if (messageFiles.size <= MAX_FORUM_MESSAGE_FILES) return
        messageFiles
            .filter { it.name != keepFileName }
            .drop(MAX_FORUM_MESSAGE_FILES - 1)
            .forEach { it.delete() }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    companion object {
        private const val CACHE_DIR_NAME = "launch_cache"
        private const val FILE_AUTH_USER = "auth_user.json"
        private const val FILE_PROFILE = "profile.json"
        private const val FILE_TEAM = "team.json"
        private const val FILE_CHAT_ROOMS = "chat_rooms.json"
        private const val FILE_REMOVED_MESSAGE_IDS = "chat_removed_message_ids.json"
        private const val MESSAGE_FILE_PREFIX = "messages_"
        private const val FORUM_MESSAGES_FILE_PREFIX = "forum_messages_"
        private const val MAX_MESSAGE_ROOM_FILES = 12
        private const val MAX_FORUM_MESSAGE_FILES = 5
        private const val MAX_REMOVED_MESSAGE_IDS = 512

        /** Soft TTL — stale data is still returned; callers refresh from network. */
        const val SOFT_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** Overlay «Участники онлайн» snapshot — short TTL for stale fallback only. */
        const val OVERLAY_PRESENCE_DISK_SOFT_TTL_MS = 5L * 60_000

        fun isStale(savedAtMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
            savedAtMs <= 0L || nowMs - savedAtMs > SOFT_TTL_MS
    }
}

@JsonClass(generateAdapter = true)
data class CachedAuthUser(
    val user: AuthUser,
    val savedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class CachedProfile(
    val profile: MyProfileDto,
    val savedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class CachedTeam(
    val team: TeamDetailDto,
    val savedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class CachedChatRooms(
    val rooms: List<ChatRoomDto>,
    val savedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class CachedRoomMessages(
    val messages: List<ChatMessage>,
    val hasMoreOlder: Boolean,
    val savedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class CachedRemovedMessageIds(
    val messageIds: List<String>,
    val savedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class CachedTeamNews(
    val page: TeamNewsListPageDto,
    val savedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class CachedForumTopics(
    val topics: List<TeamForumTopicDto>,
    val savedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class CachedForumMessages(
    val messages: List<TeamForumMessageDto>,
    val hasMoreOlder: Boolean,
    val savedAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class CachedOverlayPresence(
    val presence: TeamOverlayPresenceDto,
    val savedAtMs: Long,
)
