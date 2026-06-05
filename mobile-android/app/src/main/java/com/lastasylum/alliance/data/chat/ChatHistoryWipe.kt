package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh

/**
 * Shared local wipe after admin [chat:history:cleared] — disk, session RAM, read cursors, overlay hub floor.
 * UI state reset remains in [com.lastasylum.alliance.ui.chat.ChatViewModel].
 */
object ChatHistoryWipe {
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
