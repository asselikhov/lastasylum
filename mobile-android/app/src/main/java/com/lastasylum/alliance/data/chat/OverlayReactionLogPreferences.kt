package com.lastasylum.alliance.data.chat

import android.content.Context

/**
 * Per-user local cutoff for overlay reaction log cards (hide older entries on this device only).
 */
class OverlayReactionLogPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHiddenBeforeLogId(userId: String): String? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        return prefs.getString(hiddenKey(id), null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setHiddenBeforeLogId(userId: String, logId: String) {
        val uid = userId.trim()
        val lid = logId.trim()
        if (uid.isEmpty() || lid.isEmpty()) return
        prefs.edit().putString(hiddenKey(uid), lid).apply()
    }

    fun clearHiddenBeforeLogId(userId: String) {
        val id = userId.trim()
        if (id.isEmpty()) return
        prefs.edit().remove(hiddenKey(id)).apply()
    }

    private fun hiddenKey(userId: String): String = "overlay_reaction_log_hidden_before_$userId"

    private companion object {
        const val PREFS_NAME = "overlay_reaction_log_prefs"
    }
}
