package com.lastasylum.alliance.data.chat

import android.content.Context

/** Persists selected chat room id for the app and overlay (sync read). */
class ChatRoomPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var activeUserId: String? = null

    /** Scope read cursors to the signed-in user so badges stay per-account on this device. */
    fun bindUser(userId: String) {
        val id = userId.trim()
        activeUserId = id.takeIf { it.isNotEmpty() }
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

    fun loadAllLastReadMessageIds(): Map<String, String> {
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

    fun clear() {
        activeUserId = null
        prefs.edit()
            .remove(KEY_SELECTED_ROOM)
            .remove(KEY_RAID_ROOM)
            .remove(KEY_HUB_ROOM)
            .apply()
        val editor = prefs.edit()
        prefs.all.keys.filter { it is String && it.startsWith(KEY_LAST_READ_PREFIX) }
            .forEach { editor.remove(it as String) }
        editor.apply()
    }

    private fun lastReadKeyPrefix(): String {
        val uid = activeUserId?.trim().orEmpty()
        return if (uid.isBlank()) KEY_LAST_READ_PREFIX else "$KEY_LAST_READ_PREFIX$uid:"
    }

    private fun lastReadKey(roomId: String): String = lastReadKeyPrefix() + roomId

    private companion object {
        const val PREFS_NAME = "squadrelay_chat"
        const val KEY_SELECTED_ROOM = "selected_room_id"
        const val KEY_RAID_ROOM = "raid_room_id"
        const val KEY_HUB_ROOM = "hub_room_id"
        const val KEY_LAST_READ_PREFIX = "last_read_msg_"
    }
}
