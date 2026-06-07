package com.lastasylum.alliance.data.chat

import android.content.Context
import com.lastasylum.alliance.data.ReadCursorPrefKeys

/** Persists selected chat room id for the app and overlay (sync read). */
class ChatRoomPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var activeUserId: String? = null

    /** Scope read cursors to the signed-in user so badges stay per-account on this device. */
    fun bindUser(userId: String) {
        val id = userId.trim()
        if (id.isEmpty()) {
            activeUserId = null
            return
        }
        migrateLegacyReadCursors(id)
        activeUserId = id
    }

    fun getSelectedRoomId(): String? = prefs.getString(KEY_SELECTED_ROOM, null)

    fun setSelectedRoomId(id: String) {
        prefs.edit().putString(KEY_SELECTED_ROOM, id).apply()
    }

    /** Alliance «Рейд» room id for combat overlay strip (not necessarily the selected tab). */
    fun getRaidRoomId(): String? = prefs.getString(KEY_RAID_ROOM, null)

    fun setRaidRoomId(id: String) {
        prefs.edit().putString(KEY_RAID_ROOM, id).apply()
    }

    fun clearRaidRoomId() {
        prefs.edit().remove(KEY_RAID_ROOM).apply()
    }

    /** Alliance hub («Альянс») room id for overlay socket + HUD unread. */
    fun getHubRoomId(): String? = prefs.getString(KEY_HUB_ROOM, null)

    fun setHubRoomId(id: String) {
        prefs.edit().putString(KEY_HUB_ROOM, id).apply()
    }

    fun clearHubRoomId() {
        prefs.edit().remove(KEY_HUB_ROOM).apply()
    }

    fun getLastReadMessageId(roomId: String): String? =
        prefs.getString(lastReadKey(roomId), null)

    fun setLastReadMessageId(roomId: String, messageId: String) {
        if (roomId.isBlank() || messageId.isBlank()) return
        prefs.edit().putString(lastReadKey(roomId), messageId).apply()
    }

    fun getHiddenBeforeMessageId(roomId: String): String? =
        prefs.getString(hiddenBeforeKey(roomId), null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setHiddenBeforeMessageId(roomId: String, messageId: String) {
        if (roomId.isBlank() || messageId.isBlank()) return
        prefs.edit().putString(hiddenBeforeKey(roomId), messageId).apply()
    }

    fun clearHiddenBeforeMessageId(roomId: String) {
        if (roomId.isBlank()) return
        prefs.edit().remove(hiddenBeforeKey(roomId)).apply()
    }

    /** Drop per-room history watermarks (admin wipe / reinstall repair). */
    fun clearAllHiddenBeforeMessageIds() {
        val prefix = hiddenBeforeKeyPrefix()
        val editor = prefs.edit()
        prefs.all.keys
            .filterIsInstance<String>()
            .filter { it.startsWith(prefix) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    fun getAcknowledgedHistoryClearedAt(): String =
        prefs.getString(ackHistoryClearedKey(), null)?.trim().orEmpty()

    fun setAcknowledgedHistoryClearedAt(isoTimestamp: String) {
        val at = isoTimestamp.trim()
        if (at.isEmpty()) return
        prefs.edit().putString(ackHistoryClearedKey(), at).apply()
    }

    fun loadAllLastReadMessageIds(): Map<String, String> {
        if (activeUserId.isNullOrBlank()) return emptyMap()
        val prefix = lastReadKeyPrefix()
        return prefs.all
            .mapNotNull { (key, value) ->
                if (key is String && key.startsWith(prefix) && value is String && value.isNotBlank()) {
                    key.removePrefix(prefix) to value
                } else {
                    null
                }
            }
            .toMap()
    }

    /** Drop per-room read cursors only; keep selected / raid / hub room ids. */
    fun clearLastReadCursors() {
        val editor = prefs.edit()
        val prefix = KEY_LAST_READ_PREFIX
        prefs.all.keys
            .filterIsInstance<String>()
            .filter { it.startsWith(prefix) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    fun clear() {
        activeUserId = null
        prefs.edit()
            .remove(KEY_SELECTED_ROOM)
            .remove(KEY_RAID_ROOM)
            .remove(KEY_HUB_ROOM)
            .apply()
        clearLastReadCursors()
    }

    private fun lastReadKeyPrefix(): String {
        val uid = activeUserId?.trim().orEmpty()
        return if (uid.isBlank()) KEY_LAST_READ_PREFIX else "$KEY_LAST_READ_PREFIX$uid:"
    }

    private fun lastReadKey(roomId: String): String = lastReadKeyPrefix() + roomId

    private fun hiddenBeforeKeyPrefix(): String {
        val uid = activeUserId?.trim().orEmpty()
        return if (uid.isBlank()) KEY_HIDDEN_BEFORE_PREFIX else "$KEY_HIDDEN_BEFORE_PREFIX$uid:"
    }

    private fun hiddenBeforeKey(roomId: String): String = hiddenBeforeKeyPrefix() + roomId

    private fun ackHistoryClearedKey(): String {
        val uid = activeUserId?.trim().orEmpty()
        return if (uid.isBlank()) KEY_HISTORY_CLEARED_ACK else "$KEY_HISTORY_CLEARED_ACK$uid"
    }

    /** Pre-userId keys were `last_read_msg_{roomId}`; move them to `last_read_msg_{userId}:{roomId}`. */
    private fun migrateLegacyReadCursors(userId: String) {
        val snapshot = prefs.all
        if (snapshot.isEmpty()) return
        val editor = prefs.edit()
        var changed = false
        for ((key, value) in snapshot) {
            if (key !is String || !ReadCursorPrefKeys.isLegacyChatReadKey(key)) continue
            val roomId = key.removePrefix(KEY_LAST_READ_PREFIX)
            val messageId = (value as? String)?.trim().orEmpty()
            if (messageId.isBlank()) continue
            val scopedKey = ReadCursorPrefKeys.chatReadKey(userId, roomId)
            val merged = ReadCursorPrefKeys.mergeReadMessageIds(
                existing = prefs.getString(scopedKey, null),
                incoming = messageId,
            )
            editor.putString(scopedKey, merged)
            editor.remove(key)
            changed = true
        }
        if (changed) editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "squadrelay_chat"
        const val KEY_SELECTED_ROOM = "selected_room_id"
        const val KEY_RAID_ROOM = "raid_room_id"
        const val KEY_HUB_ROOM = "hub_room_id"
        const val KEY_LAST_READ_PREFIX = "last_read_msg_"
        const val KEY_HIDDEN_BEFORE_PREFIX = "hidden_before_msg_"
        const val KEY_HISTORY_CLEARED_ACK = "history_cleared_ack_"
    }
}
