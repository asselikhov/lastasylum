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

    /** balanced | commander | minimal — позиции FAB и тикера в оверлее. */
    fun getOverlayLayoutPreset(): String =
        prefs.getString(KEY_OVERLAY_LAYOUT_PRESET, PRESET_BALANCED) ?: PRESET_BALANCED

    fun setOverlayLayoutPreset(value: String) {
        prefs.edit().putString(KEY_OVERLAY_LAYOUT_PRESET, value).apply()
    }

    /**
     * Если включено — боевой оверлей показывается только когда в фоне открыт пакет игры
     * ([getOverlayTargetGamePackage]) и выдан доступ «Данные об использовании».
     */
    fun isOverlayGameGateEnabled(): Boolean =
        prefs.getBoolean(KEY_OVERLAY_GAME_GATE, true)

    fun setOverlayGameGateEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_GAME_GATE, value).apply()
    }

    /** applicationId игры (например com.lastasylum.plague). */
    fun getOverlayTargetGamePackage(): String =
        prefs.getString(KEY_OVERLAY_TARGET_PACKAGE, DEFAULT_TARGET_GAME_PACKAGE)
            ?: DEFAULT_TARGET_GAME_PACKAGE

    fun setOverlayTargetGamePackage(value: String) {
        prefs.edit().putString(KEY_OVERLAY_TARGET_PACKAGE, value.trim()).apply()
    }

    companion object {
        const val PRESET_BALANCED = "balanced"
        const val PRESET_COMMANDER = "commander"
        const val PRESET_MINIMAL = "minimal"

        private const val PREFS_NAME = "squadrelay_user_settings"
        private const val KEY_QUIET = "quiet_notifications"
        private const val KEY_COMPACT_OVERLAY = "compact_overlay"
        private const val KEY_OVERLAY_DRAG_LOCK = "overlay_drag_lock"
        private const val KEY_OVERLAY_LAYOUT_PRESET = "overlay_layout_preset"
        private const val KEY_OVERLAY_GAME_GATE = "overlay_game_gate"
        private const val KEY_OVERLAY_TARGET_PACKAGE = "overlay_target_game_package"

        /** Должен совпадать с [com.lastasylum.alliance.overlay.GameForegroundGate.DEFAULT_TARGET_GAME_PACKAGE]. */
        private const val DEFAULT_TARGET_GAME_PACKAGE = "com.lastasylum.plague"
    }
}
