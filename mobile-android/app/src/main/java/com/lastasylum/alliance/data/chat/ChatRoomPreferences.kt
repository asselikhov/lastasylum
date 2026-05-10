package com.lastasylum.alliance.data.chat

import android.content.Context

/** Persists selected chat room id for the app and overlay (sync read). */
class ChatRoomPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    fun clear() {
        prefs.edit()
            .remove(KEY_SELECTED_ROOM)
            .remove(KEY_RAID_ROOM)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "squadrelay_chat"
        const val KEY_SELECTED_ROOM = "selected_room_id"
        const val KEY_RAID_ROOM = "raid_room_id"
    }
}
