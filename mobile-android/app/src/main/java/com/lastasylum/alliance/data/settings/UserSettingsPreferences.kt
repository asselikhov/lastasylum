package com.lastasylum.alliance.data.settings

import android.content.Context

/** User-facing toggles from Profile; read also by [com.lastasylum.alliance.overlay.CombatOverlayService]. */
class UserSettingsPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isQuietMode(): Boolean = prefs.getBoolean(KEY_QUIET, false)

    fun setQuietMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_QUIET, value).apply()
    }

    /** Fewer floating controls: no chat strip on overlay, slightly smaller mic bubble. */
    fun isCompactOverlay(): Boolean = prefs.getBoolean(KEY_COMPACT_OVERLAY, true)

    fun setCompactOverlay(value: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT_OVERLAY, value).apply()
    }

    /** Если true — пузырёк PTT и круглые кнопки нельзя перетаскивать (случайные сдвиги). */
    fun isOverlayDragLocked(): Boolean = prefs.getBoolean(KEY_OVERLAY_DRAG_LOCK, false)

    fun setOverlayDragLocked(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_DRAG_LOCK, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "squadrelay_user_settings"
        const val KEY_QUIET = "quiet_notifications"
        const val KEY_COMPACT_OVERLAY = "compact_overlay"
        const val KEY_OVERLAY_DRAG_LOCK = "overlay_drag_lock"
    }
}
