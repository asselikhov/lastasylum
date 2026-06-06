package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.outbox.ChatOutbox
import com.lastasylum.alliance.data.chat.store.MessageStore
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared local wipe after admin [chat:history:cleared] — Room, outbox, disk, session RAM, read cursors.
 * UI state reset remains in [com.lastasylum.alliance.ui.chat.ChatViewModel].
 */
object ChatHistoryWipe {
    suspend fun wipeAllLocalChatData(
        userId: String,
        messageStore: MessageStore,
        chatOutbox: ChatOutbox,
        launchDiskCache: LaunchDiskCache,
        chatRoomPreferences: ChatRoomPreferences,
    ) = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        if (uid.isEmpty()) return@withContext
        messageStore.clearUser(uid)
        chatOutbox.clearForUser(uid)
        wipeCaches(uid, launchDiskCache, chatRoomPreferences)
    }

    fun wipeCaches(
        userId: String,
        launchDiskCache: LaunchDiskCache,
        chatRoomPreferences: ChatRoomPreferences,
    ) {
        val uid = userId.trim()
        if (uid.isNotEmpty()) {
            runCatching { launchDiskCache.clearChatHistory(uid) }
        }
        ChatSessionCache.clear()
        chatRoomPreferences.clearLastReadCursors()
        chatRoomPreferences.clearAllHiddenBeforeMessageIds()
        CombatOverlayService.clearHubUnreadState()
        OverlayGameStatusHudRefresh.invalidateNewsForumCache()
    }
}
