package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRaidRoomSync
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.chat.ChatSessionCache

/**
 * Whether a chat message belongs on the in-game overlay Raid strip, and keeping raid room prefs in sync.
 */
internal object OverlayRaidStripRouting {
    fun overlayRaidRoomIdsFromCache(prefsRaidId: String?): Set<String> {
        val ids = LinkedHashSet<String>()
        prefsRaidId?.trim()?.takeIf { it.isNotEmpty() }?.let { ids.add(it) }
        val rooms = ChatSessionCache.getFreshRooms() ?: return ids
        rooms.filter { ChatRaidRoomSync.isAllianceRaidRoom(it) }
            .mapNotNull { it.id.trim().takeIf { id -> id.isNotEmpty() } }
            .forEach { ids.add(it) }
        return ids
    }

    /**
     * True if [msg] targets the alliance «Рейд» room (prefs, cache ids, or room list metadata).
     * When the message room id is a raid room, [onRaidRoomIdResolved] may persist it to prefs.
     */
    fun acceptsRaidStripMessage(
        msg: ChatMessage,
        prefsRaidId: String?,
        onRaidRoomIdResolved: ((String) -> Unit)? = null,
        trustedRaidRoomIds: Set<String> = emptySet(),
    ): Boolean {
        val room = msg.roomId.trim()
        val raidIds = overlayRaidRoomIdsFromCache(prefsRaidId) + trustedRaidRoomIds
        if (OverlayStripMessageRouter.isOverlayRaidRoomMessage(msg, prefsRaidId, raidIds)) {
            if (room.isNotEmpty()) onRaidRoomIdResolved?.invoke(room)
            return true
        }
        if (room.isEmpty()) return false
        if (room in trustedRaidRoomIds) {
            onRaidRoomIdResolved?.invoke(room)
            return true
        }
        val prefsRaid = prefsRaidId?.trim().orEmpty()
        if (prefsRaid.isNotEmpty() && room == prefsRaid) {
            onRaidRoomIdResolved?.invoke(room)
            return true
        }
        val rooms = ChatSessionCache.getFreshRooms() ?: return false
        val dto = rooms.firstOrNull { it.id.trim() == room } ?: return false
        if (!ChatRaidRoomSync.isAllianceRaidRoom(dto)) return false
        onRaidRoomIdResolved?.invoke(room)
        return true
    }

    fun raidRoomFromRooms(rooms: List<ChatRoomDto>, preferences: ChatRoomPreferences): String? {
        ChatRaidRoomSync.applyRaidRoomPreference(rooms, preferences)
        return preferences.getRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
