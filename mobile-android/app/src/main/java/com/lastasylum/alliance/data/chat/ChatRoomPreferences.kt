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

    fun clear() {
        prefs.edit().remove(KEY_SELECTED_ROOM).apply()
    }

    private companion object {
        const val PREFS_NAME = "squadrelay_chat"
        const val KEY_SELECTED_ROOM = "selected_room_id"
    }
}
