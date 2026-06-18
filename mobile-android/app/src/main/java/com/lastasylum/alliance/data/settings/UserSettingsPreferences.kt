package com.lastasylum.alliance.data.settings

import android.content.Context

/** User-facing toggles from Profile; read also by [com.lastasylum.alliance.overlay.CombatOverlayService]. */
class UserSettingsPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var activeUserId: String? = null

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

    /**
     * HUD chips only in-game — no chat strip window (lower CPU/RAM on weak devices).
     */
    fun isOverlayHudOnlyMode(): Boolean = prefs.getBoolean(KEY_OVERLAY_HUD_ONLY, false)

    fun setOverlayHudOnlyMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_HUD_ONLY, value).apply()
    }

    /** Lighter chat strip: no image previews in raid overlay bubbles. */
    fun isOverlayLightStrip(): Boolean = prefs.getBoolean(KEY_OVERLAY_LIGHT_STRIP, false)

    fun setOverlayLightStrip(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_LIGHT_STRIP, value).apply()
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
        prefs.getBoolean(KEY_OVERLAY_VOICE_SOUND, false)

    fun setOverlayVoiceSoundEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_VOICE_SOUND, value).apply()
    }

    /** Передавать свой голос в рейд (оверлей). */
    fun isOverlayVoiceMicEnabled(): Boolean =
        prefs.getBoolean(KEY_OVERLAY_VOICE_MIC, false)

    fun setOverlayVoiceMicEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_VOICE_MIC, value).apply()
    }

    /** Громкость приёма голоса союзников (0…1.5, 1 = 100%). */
    fun getOverlayVoiceSoundVolume(): Float =
        effectiveOverlayVoiceVolume(
            prefs.getFloat(KEY_OVERLAY_VOICE_SOUND_VOLUME, OVERLAY_VOICE_VOLUME_DEFAULT),
        )

    fun setOverlayVoiceSoundVolume(value: Float) {
        prefs.edit()
            .putFloat(KEY_OVERLAY_VOICE_SOUND_VOLUME, clampOverlayVoiceVolume(value))
            .apply()
    }

    /** Громкость передачи микрофона (0…1.5, 1 = 100%). */
    fun getOverlayVoiceMicVolume(): Float =
        effectiveOverlayVoiceVolume(
            prefs.getFloat(KEY_OVERLAY_VOICE_MIC_VOLUME, OVERLAY_VOICE_VOLUME_DEFAULT),
        )

    fun setOverlayVoiceMicVolume(value: Float) {
        prefs.edit()
            .putFloat(KEY_OVERLAY_VOICE_MIC_VOLUME, clampOverlayVoiceVolume(value))
            .apply()
    }

    private fun clampOverlayVoiceVolume(value: Float): Float =
        value.coerceIn(OVERLAY_VOICE_VOLUME_MIN, OVERLAY_VOICE_VOLUME_MAX)

    /** Zero gain makes voice inaudible; treat as unset and use default. */
    private fun effectiveOverlayVoiceVolume(stored: Float): Float =
        if (stored <= 0f) OVERLAY_VOICE_VOLUME_DEFAULT else clampOverlayVoiceVolume(stored)

    /** @deprecated Use [isGameEventPushEnabled] for hq_excavation. */
    fun isExcavationPushEnabled(): Boolean = isGameEventPushEnabled("hq_excavation")

    fun setExcavationPushEnabled(value: Boolean) {
        setGameEventPushEnabled("hq_excavation", value)
    }

    fun isGameEventPushEnabled(eventId: String): Boolean {
        val key = gameEventPushKey(eventId)
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, true)
        }
        return prefs.getBoolean(KEY_EXCAVATION_PUSH, true)
    }

    fun setGameEventPushEnabled(eventId: String, value: Boolean) {
        prefs.edit().putBoolean(gameEventPushKey(eventId), value).apply()
        if (eventId == "hq_excavation") {
            prefs.edit().putBoolean(KEY_EXCAVATION_PUSH, value).apply()
        }
    }

    fun applyGameEventPushEnabledFromServer(map: Map<String, Boolean>) {
        val editor = prefs.edit()
        for (event in com.lastasylum.alliance.gameevents.GameEventCatalog.all) {
            val enabled = map[event.id] ?: true
            editor.putBoolean(gameEventPushKey(event.id), enabled)
        }
        editor.putBoolean(
            KEY_EXCAVATION_PUSH,
            map["hq_excavation"] ?: true,
        )
        editor.apply()
    }

    private fun gameEventPushKey(eventId: String): String =
        "$KEY_GAME_EVENT_PUSH_PREFIX$eventId"

    fun bindUser(userId: String) {
        val id = userId.trim()
        if (id.isEmpty()) {
            activeUserId = null
            return
        }
        migrateLegacyNewsCursor(id)
        activeUserId = id
    }

    fun clearNewsReadCursor() {
        val editor = prefs.edit()
        editor.remove(KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT)
        val uid = activeUserId?.trim().orEmpty()
        if (uid.isNotEmpty()) {
            val prefix = "$KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT:$uid:"
            prefs.all.keys.filter { it.startsWith(prefix) || it == "$KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT:$uid" }
                .forEach { editor.remove(it) }
        }
        editor.apply()
    }

    /** ISO-8601: newest team news the user has seen for [teamId] (overlay HUD + news badge). */
    fun getLastSeenTeamNewsCreatedAt(teamId: String): String? {
        val tid = teamId.trim()
        if (tid.isEmpty()) return null
        migrateLegacyNewsCursorForTeam(tid)
        return prefs.getString(newsCursorKey(teamId = tid), null)?.takeIf { it.isNotBlank() }
    }

    fun setLastSeenTeamNewsCreatedAt(teamId: String, iso: String?) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        migrateLegacyNewsCursorForTeam(tid)
        val edit = prefs.edit()
        val key = newsCursorKey(teamId = tid)
        if (iso.isNullOrBlank()) {
            edit.remove(key)
        } else {
            edit.putString(key, iso.trim())
        }
        edit.apply()
    }

    private fun newsCursorKey(userId: String? = activeUserId, teamId: String): String {
        val uid = userId?.trim().orEmpty()
        val tid = teamId.trim()
        return if (uid.isBlank()) {
            "$KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT:$tid"
        } else {
            "$KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT:$uid:$tid"
        }
    }

    /** Migrate per-user global cursor to per-user per-team on first team access. */
    private fun migrateLegacyNewsCursorForTeam(teamId: String) {
        val uid = activeUserId?.trim().orEmpty()
        if (uid.isNotEmpty()) {
            migrateLegacyNewsCursor(uid)
        }
        val scoped = newsCursorKey(teamId = teamId)
        if (prefs.contains(scoped)) return
        val legacyPerUser = if (uid.isNotBlank()) {
            prefs.getString("$KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT:$uid", null)?.trim().orEmpty()
        } else {
            prefs.getString(KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT, null)?.trim().orEmpty()
        }
        if (legacyPerUser.isNotBlank()) {
            prefs.edit().putString(scoped, legacyPerUser).apply()
        }
    }

    /** Legacy key was global; move to per-user on first bind after login. */
    private fun migrateLegacyNewsCursor(userId: String) {
        val legacy = prefs.getString(KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT, null)?.trim().orEmpty()
        if (legacy.isBlank()) return
        val scoped = "$KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT:$userId"
        val existing = prefs.getString(scoped, null)?.trim().orEmpty()
        val edit = prefs.edit()
        if (existing.isBlank()) {
            edit.putString(scoped, legacy)
        }
        edit.remove(KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT)
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
    fun getOverlayPinnedMemberIds(teamId: String): Set<String> =
        getOverlayPinnedMemberIdsOrdered(teamId).toSet()

    fun getOverlayPinnedMemberIdsOrdered(teamId: String): List<String> {
        val tid = teamId.trim()
        if (tid.isEmpty()) return emptyList()
        val raw = prefs.getString(overlayPinnedKey(tid), null) ?: return emptyList()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(OVERLAY_PINNED_MAX)
    }

    fun setOverlayPinnedMemberIds(teamId: String, userIds: Collection<String>) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val csv = userIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(OVERLAY_PINNED_MAX)
            .joinToString(",")
        prefs.edit().putString(overlayPinnedKey(tid), csv).apply()
    }

    fun toggleOverlayPinnedMember(teamId: String, userId: String): Set<String> {
        val uid = userId.trim()
        if (uid.isEmpty()) return getOverlayPinnedMemberIds(teamId)
        val current = getOverlayPinnedMemberIds(teamId).toMutableSet()
        if (uid in current) {
            current.remove(uid)
        } else if (current.size < OVERLAY_PINNED_MAX) {
            current.add(uid)
        }
        setOverlayPinnedMemberIds(teamId, current)
        return current
    }

    private fun overlayPinnedKey(teamId: String): String = "$KEY_OVERLAY_PINNED_PREFIX$teamId"

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
        private const val KEY_OVERLAY_HUD_ONLY = "overlay_hud_only_v1"
        private const val KEY_OVERLAY_LIGHT_STRIP = "overlay_light_strip_v1"
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
        private const val KEY_OVERLAY_VOICE_SOUND_VOLUME = "overlay_voice_sound_volume"
        private const val KEY_OVERLAY_VOICE_MIC_VOLUME = "overlay_voice_mic_volume"
        const val OVERLAY_VOICE_VOLUME_MIN = 0f
        const val OVERLAY_VOICE_VOLUME_MAX = 1.5f
        const val OVERLAY_VOICE_VOLUME_DEFAULT = 1f
        private const val KEY_EXCAVATION_PUSH = "excavation_push_enabled"
        private const val KEY_GAME_EVENT_PUSH_PREFIX = "game_event_push_"
        private const val KEY_LAST_SEEN_TEAM_NEWS_CREATED_AT = "last_seen_team_news_created_at"
        private const val KEY_OVERLAY_TARGET_LEGACY_MIGRATED = "overlay_target_legacy_migrated_v1"
        private const val KEY_OVERLAY_TARGET_PLAY_MIGRATED = "overlay_target_play_migrated_v2"
        private const val KEY_OVERLAY_PINNED_PREFIX = "overlay_online_pinned_v1_"
        private const val OVERLAY_PINNED_MAX = 3

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
