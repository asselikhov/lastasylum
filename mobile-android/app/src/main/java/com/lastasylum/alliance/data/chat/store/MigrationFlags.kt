package com.lastasylum.alliance.data.chat.store

import android.content.Context

private const val PREFS = "squadrelay_chat_migration"
private const val KEY_IMPORTED = "chat_room_imported_v1"
private const val KEY_FORUM_IMPORTED = "forum_room_imported_v1"

object MigrationFlags {
    fun isChatDiskImported(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IMPORTED, false)

    fun markChatDiskImported(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IMPORTED, true)
            .apply()
    }

    fun isForumDiskImported(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FORUM_IMPORTED, false)

    fun markForumDiskImported(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FORUM_IMPORTED, true)
            .apply()
    }
}
