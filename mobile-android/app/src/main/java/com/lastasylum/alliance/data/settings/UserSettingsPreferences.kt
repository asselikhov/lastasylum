package com.lastasylum.alliance.data.settings

import android.content.Context

/** User-facing toggles from Profile; read also by [com.lastasylum.alliance.overlay.CombatOverlayService]. */
class UserSettingsPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * «Показывать панель»: при включении [CombatOverlayService] работает в фоне и рисует оверлей
     * только когда в foreground целевая игра ([getOverlayTargetGamePackages]).
     */
    fun isOverlayPanelEnabled(): Boolean {
        migrateOverlayPrefsIfNeeded()
        return prefs.getBoolean(KEY_OVERLAY_PANEL_ENABLED, true)
    }

    fun setOverlayPanelEnabled(value: Boolean) {
        migrateOverlayPrefsIfNeeded()
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
     * Позиция главной панели оверлея (px): x — от левого края, y — от верхнего (gravity=TOP|START).
     */
    fun getOverlayPanelPosXPx(): Int? =
        prefs.getInt(KEY_OVERLAY_PANEL_POS_X_PX, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

    fun getOverlayPanelPosYPx(): Int? =
        prefs.getInt(KEY_OVERLAY_PANEL_POS_Y_PX, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

    /**
     * Конвертирует legacy-y (от нижнего края) в y от верха один раз после обновления.
     */
    fun resolveOverlayPanelTopYPx(
        screenHeightPx: Int,
        savedYPx: Int?,
        defaultTopYPx: Int,
        fallbackPanelHeightPx: Int,
    ): Int {
        val maxY = (screenHeightPx - fallbackPanelHeightPx).coerceAtLeast(0)
        if (savedYPx == null) {
            if (!prefs.getBoolean(KEY_OVERLAY_PANEL_Y_TOP, false)) {
                prefs.edit().putBoolean(KEY_OVERLAY_PANEL_Y_TOP, true).apply()
            }
            return defaultTopYPx.coerceIn(0, maxY)
        }
        if (prefs.getBoolean(KEY_OVERLAY_PANEL_Y_TOP, false)) {
            return savedYPx.coerceIn(0, maxY)
        }
        val topY = (screenHeightPx - savedYPx - fallbackPanelHeightPx).coerceIn(0, maxY)
        prefs.edit()
            .putInt(KEY_OVERLAY_PANEL_POS_Y_PX, topY)
            .putBoolean(KEY_OVERLAY_PANEL_Y_TOP, true)
            .apply()
        return topY
    }

    fun setOverlayPanelPosPx(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_OVERLAY_PANEL_POS_X_PX, x)
            .putInt(KEY_OVERLAY_PANEL_POS_Y_PX, y)
            .apply()
    }

    fun clearOverlayPanelPosPx() {
        prefs.edit()
            .remove(KEY_OVERLAY_PANEL_POS_X_PX)
            .remove(KEY_OVERLAY_PANEL_POS_Y_PX)
            .apply()
    }

    /** Слышать голос союзников в рейде (оверлей). */
    fun isOverlayVoiceSoundEnabled(): Boolean =
        prefs.getBoolean(KEY_OVERLAY_VOICE_SOUND, true)

    fun setOverlayVoiceSoundEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_VOICE_SOUND, value).apply()
    }

    /** Передавать свой голос в рейд (оверлей). */
    fun isOverlayVoiceMicEnabled(): Boolean =
        prefs.getBoolean(KEY_OVERLAY_VOICE_MIC, false)

    fun setOverlayVoiceMicEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_VOICE_MIC, value).apply()
    }

    /** Push о координатах раскопок (сервер + локальный кэш). */
    fun isExcavationPushEnabled(): Boolean =
        prefs.getBoolean(KEY_EXCAVATION_PUSH, true)

    fun setExcavationPushEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_EXCAVATION_PUSH, value).apply()
    }

    /** ISO-8601: newest team news the user has seen (overlay HUD + news badge). */
    fun getLastSeenTeamNewsCreatedAt(): String? =
        prefs.getString(KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT, null)?.takeIf { it.isNotBlank() }

    fun setLastSeenTeamNewsCreatedAt(iso: String?) {
        val edit = prefs.edit()
        if (iso.isNullOrBlank()) {
            edit.remove(KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT)
        } else {
            edit.putString(KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT, iso.trim())
        }
        edit.apply()
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

    /**
     * Optional activity filter for the game: comma-separated substrings of activity class names.
     * When non-empty, the overlay is shown only when UsageEvents reports a resumed activity whose className
     * contains one of these tokens (case-insensitive).
     */
    fun getOverlayTargetGameActivityTokens(): List<String> =
        prefs.getString(KEY_OVERLAY_TARGET_ACTIVITY_TOKENS, "")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()

    fun setOverlayTargetGameActivityTokens(value: String) {
        prefs.edit().putString(KEY_OVERLAY_TARGET_ACTIVITY_TOKENS, value.trim()).apply()
    }

    fun setOverlayTargetGamePackage(value: String) {
        prefs.edit().putString(KEY_OVERLAY_TARGET_PACKAGE, value.trim()).apply()
    }

    /**
     * Раньше «Показывать панель» и «Только в игре» были разными ключами.
     * Панель включена без гейта → оставляем включённой (поведение станет «только в игре»).
     * Панель выключена, гейт включён → включаем панель (пользователь явно хотел режим в игре).
     */
    private fun migrateOverlayPrefsIfNeeded() {
        if (prefs.getBoolean(KEY_OVERLAY_GAME_GATE_MERGED, false)) return
        val panel = prefs.getBoolean(KEY_OVERLAY_PANEL_ENABLED, true)
        val legacyGameGate = prefs.getBoolean(KEY_OVERLAY_GAME_GATE, false)
        val mergedPanel = panel || legacyGameGate
        prefs.edit()
            .putBoolean(KEY_OVERLAY_PANEL_ENABLED, mergedPanel)
            .putBoolean(KEY_OVERLAY_GAME_GATE_MERGED, true)
            .remove(KEY_OVERLAY_GAME_GATE)
            .apply()
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
        private const val KEY_OVERLAY_GAME_GATE_MERGED = "overlay_game_gate_merged_v1"
        private const val KEY_OVERLAY_TARGET_PACKAGE = "overlay_target_game_package"
        private const val KEY_OVERLAY_TARGET_ACTIVITY_TOKENS = "overlay_target_game_activity_tokens"
        private const val KEY_OVERLAY_PANEL_POS_X_PX = "overlay_panel_pos_x_px"
        private const val KEY_OVERLAY_PANEL_POS_Y_PX = "overlay_panel_pos_y_px"
        private const val KEY_OVERLAY_PANEL_Y_TOP = "overlay_panel_y_top_gravity"
        private const val KEY_OVERLAY_VOICE_SOUND = "overlay_voice_sound"
        private const val KEY_OVERLAY_VOICE_MIC = "overlay_voice_mic"
        private const val KEY_EXCAVATION_PUSH = "excavation_push_enabled"
        private const val KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT = "last_seen_team_news_created_at"
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
