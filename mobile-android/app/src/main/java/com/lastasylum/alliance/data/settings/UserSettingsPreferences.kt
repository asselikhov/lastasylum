package com.lastasylum.alliance.data.settings

import android.content.Context

/** User-facing toggles from Profile; read also by [com.lastasylum.alliance.overlay.CombatOverlayService]. */
class UserSettingsPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Главный переключатель "Показывать панель" (источник истины для сервиса и UI). */
    fun isOverlayPanelEnabled(): Boolean = prefs.getBoolean(KEY_OVERLAY_PANEL_ENABLED, true)

    fun setOverlayPanelEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_PANEL_ENABLED, value).apply()
    }

    fun isQuietMode(): Boolean = prefs.getBoolean(KEY_QUIET, false)

    fun setQuietMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_QUIET, value).apply()
    }

    /** Slightly smaller mic bubble in overlay; chat strip is always shown. */
    fun isCompactOverlay(): Boolean = prefs.getBoolean(KEY_COMPACT_OVERLAY, false)

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
     * Если включено — боевой оверлей в идеале только при открытой игре ([getOverlayTargetGamePackage]).
     * На части прошивок без «Данных об использовании» гейт не может это проверить — тогда панель всё равно
     * показывается (см. CombatOverlayService).
     * По умолчанию выключено, чтобы после установки панель работала на любом устройстве без системных настроек.
     */
    fun isOverlayGameGateEnabled(): Boolean =
        prefs.getBoolean(KEY_OVERLAY_GAME_GATE, false)

    fun setOverlayGameGateEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_GAME_GATE, value).apply()
    }

    /** applicationId игры (например com.lastasylum.plague). Несколько — через запятую. */
    fun getOverlayTargetGamePackage(): String {
        var raw = prefs.getString(KEY_OVERLAY_TARGET_PACKAGE, DEFAULT_TARGET_GAME_PACKAGES_CSV)
            ?.trim()
            .orEmpty()
        if (raw.isEmpty()) raw = DEFAULT_TARGET_GAME_PACKAGES_CSV
        if (!prefs.getBoolean(KEY_OVERLAY_TARGET_LEGACY_MIGRATED, false)) {
            when {
                raw == LEGACY_SINGLE_TARGET_PACKAGE -> {
                    raw = DEFAULT_TARGET_GAME_PACKAGES_CSV
                    prefs.edit()
                        .putString(KEY_OVERLAY_TARGET_PACKAGE, raw)
                        .putBoolean(KEY_OVERLAY_TARGET_LEGACY_MIGRATED, true)
                        .apply()
                }
                else -> prefs.edit().putBoolean(KEY_OVERLAY_TARGET_LEGACY_MIGRATED, true).apply()
            }
        }
        if (!prefs.getBoolean(KEY_OVERLAY_TARGET_PLAY_MIGRATED, false)) {
            when {
                raw == LEGACY_PRESETS_WITHOUT_PHS || raw == LEGACY_SINGLE_TARGET_PACKAGE -> {
                    raw = DEFAULT_TARGET_GAME_PACKAGES_CSV
                    prefs.edit()
                        .putString(KEY_OVERLAY_TARGET_PACKAGE, raw)
                        .putBoolean(KEY_OVERLAY_TARGET_PLAY_MIGRATED, true)
                        .apply()
                }
                else -> prefs.edit().putBoolean(KEY_OVERLAY_TARGET_PLAY_MIGRATED, true).apply()
            }
        }
        return raw
    }

    /** Пакеты игры для гейта оверлея: один или несколько через запятую (release / debug и т.д.). */
    fun getOverlayTargetGamePackages(): List<String> =
        getOverlayTargetGamePackage()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

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
        private const val KEY_OVERLAY_PANEL_ENABLED = "overlay_panel_enabled"
        private const val KEY_OVERLAY_GAME_GATE = "overlay_game_gate"
        private const val KEY_OVERLAY_TARGET_PACKAGE = "overlay_target_game_package"
        private const val KEY_OVERLAY_TARGET_LEGACY_MIGRATED = "overlay_target_legacy_migrated_v1"
        private const val KEY_OVERLAY_TARGET_PLAY_MIGRATED = "overlay_target_play_migrated_v2"

        /**
         * Google Play (RU): `com.phs.global` — см. сведения об игре; также варианты com.lastasylum.plague*.
         */
        private const val DEFAULT_TARGET_GAME_PACKAGES_CSV =
            "com.phs.global,com.lastasylum.plague,com.lastasylum.plague.debug"

        private const val LEGACY_SINGLE_TARGET_PACKAGE = "com.lastasylum.plague"

        /** Старый дефолт без Google Play-пакета — дополняем до [DEFAULT_TARGET_GAME_PACKAGES_CSV]. */
        private const val LEGACY_PRESETS_WITHOUT_PHS =
            "com.lastasylum.plague,com.lastasylum.plague.debug"
    }
}
