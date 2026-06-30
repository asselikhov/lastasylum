package com.lastasylum.alliance.data.settings

import android.content.Context

/** Snapshot of auto-assault fields edited in the overlay «Цель → Штурм» tab. */
data class AutoAssaultOverlayDraft(
    val squads: Set<Int>,
    val allowedMemberIds: Set<String>,
    val targetTypes: Set<String>,
    val squadPowerMin: List<Long>,
    val squadPowerMax: List<Long>,
    val maxDistanceCreator: Int,
    val maxDistanceTarget: Int,
    val levelMin: Int,
    val levelMax: Int,
    val minRemainingSec: Int,
    val cooldownSec: Int,
    val maxConcurrent: Int,
    val durationMin: Int,
)

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

    /**
     * Авто-помощь соальянсникам: пока включено, in-process мост пропатченной игры периодически
     * вызывает «помочь всем» (UnionHelpAllC2S) — то же сетевое действие, что и кнопка «Помощь».
     */
    fun isAutoHelpEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_HELP_ENABLED, false)

    fun setAutoHelpEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HELP_ENABLED, value).apply()
    }

    /** Интервал авто-помощи в секундах (зажат в [AUTO_HELP_INTERVAL_MIN_SEC]…[AUTO_HELP_INTERVAL_MAX_SEC]). */
    fun getAutoHelpIntervalSec(): Int =
        prefs.getInt(KEY_AUTO_HELP_INTERVAL_SEC, AUTO_HELP_INTERVAL_DEFAULT_SEC)
            .coerceIn(AUTO_HELP_INTERVAL_MIN_SEC, AUTO_HELP_INTERVAL_MAX_SEC)

    fun setAutoHelpIntervalSec(value: Int) {
        prefs.edit()
            .putInt(
                KEY_AUTO_HELP_INTERVAL_SEC,
                value.coerceIn(AUTO_HELP_INTERVAL_MIN_SEC, AUTO_HELP_INTERVAL_MAX_SEC),
            )
            .apply()
    }

    /**
     * Авто-штурм: вступление в ралли соалийцев по правилам из оверлея.
     * Учитывает авто-выключение по таймеру: если срок истёк — считаем выключенным.
     */
    fun isAutoAssaultEnabled(): Boolean {
        if (!prefs.getBoolean(KEY_AUTO_ASSAULT_ENABLED, false)) return false
        val disableAt = prefs.getLong(KEY_AUTO_ASSAULT_DISABLE_AT, 0L)
        if (disableAt in 1 until System.currentTimeMillis()) {
            prefs.edit().putBoolean(KEY_AUTO_ASSAULT_ENABLED, false).apply()
            return false
        }
        return true
    }

    /** Сырое сохранённое значение без учёта истечения таймера (для отображения переключателя). */
    fun isAutoAssaultEnabledRaw(): Boolean = prefs.getBoolean(KEY_AUTO_ASSAULT_ENABLED, false)

    fun setAutoAssaultEnabled(value: Boolean) {
        val edit = prefs.edit().putBoolean(KEY_AUTO_ASSAULT_ENABLED, value)
        if (value) {
            val durationMin = getAutoAssaultDurationMin()
            if (durationMin > 0) {
                edit.putLong(
                    KEY_AUTO_ASSAULT_DISABLE_AT,
                    System.currentTimeMillis() + durationMin * 60_000L,
                )
            } else {
                edit.putLong(KEY_AUTO_ASSAULT_DISABLE_AT, 0L)
            }
        } else {
            edit.putLong(KEY_AUTO_ASSAULT_DISABLE_AT, 0L)
        }
        edit.commit()
    }

    /** Эпоха (мс), когда авто-штурм должен сам выключиться (0 = бессрочно). */
    fun getAutoAssaultDisableAtMs(): Long = prefs.getLong(KEY_AUTO_ASSAULT_DISABLE_AT, 0L)

    /** Длительность авто-штурма в минутах перед авто-выключением (0 = бессрочно). */
    fun getAutoAssaultDurationMin(): Int =
        prefs.getInt(KEY_AUTO_ASSAULT_DURATION_MIN, 0).coerceIn(0, AUTO_ASSAULT_DURATION_MAX_MIN)

    fun setAutoAssaultDurationMin(value: Int) {
        prefs.edit()
            .putInt(KEY_AUTO_ASSAULT_DURATION_MIN, value.coerceIn(0, AUTO_ASSAULT_DURATION_MAX_MIN))
            .apply()
        if (isAutoAssaultEnabledRaw()) {
            // Пересчитать срок выключения под новую длительность.
            setAutoAssaultEnabled(true)
        }
    }

    /** Разрешённые типы цели: набор из [AUTO_ASSAULT_TYPE_MONSTER]/PLAYER/CITY. */
    fun getAutoAssaultTargetTypes(): Set<String> {
        val stored = prefs.getStringSet(KEY_AUTO_ASSAULT_TARGET_TYPES, null)
        return if (stored.isNullOrEmpty()) AUTO_ASSAULT_TYPES_ALL else stored.toSet()
    }

    fun setAutoAssaultTargetTypes(types: Set<String>) {
        val sanitized = types.filter { it in AUTO_ASSAULT_TYPES_ALL }.toSet()
            .ifEmpty { AUTO_ASSAULT_TYPES_ALL }
        prefs.edit().putStringSet(KEY_AUTO_ASSAULT_TARGET_TYPES, sanitized).apply()
    }

    /** Минимальный уровень цели (0 = без ограничения). */
    fun getAutoAssaultTargetLevelMin(): Int =
        prefs.getInt(KEY_AUTO_ASSAULT_LEVEL_MIN, 0).coerceIn(0, AUTO_ASSAULT_LEVEL_MAX)

    fun setAutoAssaultTargetLevelMin(value: Int) {
        prefs.edit().putInt(KEY_AUTO_ASSAULT_LEVEL_MIN, value.coerceIn(0, AUTO_ASSAULT_LEVEL_MAX)).apply()
    }

    /** Максимальный уровень цели (0 = без ограничения). */
    fun getAutoAssaultTargetLevelMax(): Int =
        prefs.getInt(KEY_AUTO_ASSAULT_LEVEL_MAX, 0).coerceIn(0, AUTO_ASSAULT_LEVEL_MAX)

    fun setAutoAssaultTargetLevelMax(value: Int) {
        prefs.edit().putInt(KEY_AUTO_ASSAULT_LEVEL_MAX, value.coerceIn(0, AUTO_ASSAULT_LEVEL_MAX)).apply()
    }

    /** Не вступать, если до отправки штурма осталось меньше N секунд. */
    fun getAutoAssaultMinRemainingSec(): Int =
        prefs.getInt(KEY_AUTO_ASSAULT_MIN_REMAINING_SEC, AUTO_ASSAULT_MIN_REMAINING_DEFAULT_SEC)
            .coerceIn(0, AUTO_ASSAULT_MIN_REMAINING_MAX_SEC)

    fun setAutoAssaultMinRemainingSec(value: Int) {
        prefs.edit()
            .putInt(
                KEY_AUTO_ASSAULT_MIN_REMAINING_SEC,
                value.coerceIn(0, AUTO_ASSAULT_MIN_REMAINING_MAX_SEC),
            )
            .apply()
    }

    /** Кулдаун между авто-вступлениями (секунды). */
    fun getAutoAssaultCooldownSec(): Int =
        prefs.getInt(KEY_AUTO_ASSAULT_COOLDOWN_SEC, AUTO_ASSAULT_COOLDOWN_DEFAULT_SEC)
            .coerceIn(AUTO_ASSAULT_COOLDOWN_MIN_SEC, AUTO_ASSAULT_COOLDOWN_MAX_SEC)

    fun setAutoAssaultCooldownSec(value: Int) {
        prefs.edit()
            .putInt(
                KEY_AUTO_ASSAULT_COOLDOWN_SEC,
                value.coerceIn(AUTO_ASSAULT_COOLDOWN_MIN_SEC, AUTO_ASSAULT_COOLDOWN_MAX_SEC),
            )
            .apply()
    }

    /** Максимум одновременных авто-маршей (0 = без ограничения). */
    fun getAutoAssaultMaxConcurrent(): Int =
        prefs.getInt(KEY_AUTO_ASSAULT_MAX_CONCURRENT, 0).coerceIn(0, AUTO_ASSAULT_MAX_CONCURRENT_CAP)

    fun setAutoAssaultMaxConcurrent(value: Int) {
        prefs.edit()
            .putInt(KEY_AUTO_ASSAULT_MAX_CONCURRENT, value.coerceIn(0, AUTO_ASSAULT_MAX_CONCURRENT_CAP))
            .apply()
    }

    /** Лог последних авто-вступлений (свежие сверху), JSON-массив строк. */
    fun getAutoAssaultJoinLog(): List<String> {
        val raw = prefs.getString(KEY_AUTO_ASSAULT_JOIN_LOG, null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    /** Добавить запись в лог (хранится не более [AUTO_ASSAULT_JOIN_LOG_MAX]). */
    fun appendAutoAssaultJoinLog(entry: String) {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return
        val current = getAutoAssaultJoinLog().toMutableList()
        current.add(0, trimmed)
        while (current.size > AUTO_ASSAULT_JOIN_LOG_MAX) current.removeAt(current.size - 1)
        val arr = org.json.JSONArray()
        current.forEach { arr.put(it) }
        prefs.edit().putString(KEY_AUTO_ASSAULT_JOIN_LOG, arr.toString()).apply()
    }

    fun clearAutoAssaultJoinLog() {
        prefs.edit().remove(KEY_AUTO_ASSAULT_JOIN_LOG).apply()
    }

    /** Сырой JSON-ростер альянса из игрового моста ([{id,name,power,level,rank}]). */
    fun getAllianceRosterJson(): String? = prefs.getString(KEY_ALLIANCE_ROSTER_JSON, null)

    fun setAllianceRosterJson(json: String) {
        prefs.edit().putString(KEY_ALLIANCE_ROSTER_JSON, json).apply()
    }

    /** Индексы отрядов 0…2 (в игре teamIndex), CSV «0,1,2». */
    fun getAutoAssaultSquads(): Set<Int> = parseCsvInts(prefs.getString(KEY_AUTO_ASSAULT_SQUADS, "0,1,2"))
        .filter { it in AUTO_ASSAULT_SQUAD_MIN..AUTO_ASSAULT_SQUAD_MAX }
        .toSet()
        .ifEmpty { setOf(0) }

    fun setAutoAssaultSquads(indices: Set<Int>) {
        val csv = indices
            .filter { it in AUTO_ASSAULT_SQUAD_MIN..AUTO_ASSAULT_SQUAD_MAX }
            .sorted()
            .joinToString(",")
        prefs.edit().putString(KEY_AUTO_ASSAULT_SQUADS, csv.ifEmpty { "0" }).apply()
    }

    fun getAutoAssaultSquadPowerMin(squadIndex: Int): Long =
        prefs.getLong(squadPowerMinKey(squadIndex), 0L).coerceAtLeast(0L)

    fun setAutoAssaultSquadPowerMin(squadIndex: Int, value: Long) {
        prefs.edit()
            .putLong(squadPowerMinKey(squadIndex), value.coerceAtLeast(0L))
            .apply()
    }

    fun getAutoAssaultSquadPowerMax(squadIndex: Int): Long =
        prefs.getLong(squadPowerMaxKey(squadIndex), AUTO_ASSAULT_POWER_MAX_DEFAULT)
            .coerceIn(0L, AUTO_ASSAULT_POWER_CEILING)

    fun setAutoAssaultSquadPowerMax(squadIndex: Int, value: Long) {
        prefs.edit()
            .putLong(
                squadPowerMaxKey(squadIndex),
                value.coerceIn(0L, AUTO_ASSAULT_POWER_CEILING),
            )
            .apply()
    }

    /** Максимальная дистанция до цели (клетки карты, Chebyshev от базы). Устаревшее — см. creator/target. */
    fun getAutoAssaultMaxDistance(): Int =
        prefs.getInt(KEY_AUTO_ASSAULT_MAX_DISTANCE, AUTO_ASSAULT_MAX_DISTANCE_DEFAULT)
            .coerceIn(AUTO_ASSAULT_MAX_DISTANCE_MIN, AUTO_ASSAULT_MAX_DISTANCE_MAX)

    fun setAutoAssaultMaxDistance(value: Int) {
        val v = value.coerceIn(AUTO_ASSAULT_MAX_DISTANCE_MIN, AUTO_ASSAULT_MAX_DISTANCE_MAX)
        prefs.edit()
            .putInt(KEY_AUTO_ASSAULT_MAX_DISTANCE, v)
            .apply()
    }

    /** Макс. дистанция до города создателя штурма / точки сбора (rallyPoint). */
    fun getAutoAssaultMaxDistanceCreator(): Int =
        prefs.getInt(
            KEY_AUTO_ASSAULT_MAX_DISTANCE_CREATOR,
            getAutoAssaultMaxDistance(),
        ).coerceIn(AUTO_ASSAULT_MAX_DISTANCE_MIN, AUTO_ASSAULT_MAX_DISTANCE_MAX)

    fun setAutoAssaultMaxDistanceCreator(value: Int) {
        prefs.edit()
            .putInt(
                KEY_AUTO_ASSAULT_MAX_DISTANCE_CREATOR,
                value.coerceIn(AUTO_ASSAULT_MAX_DISTANCE_MIN, AUTO_ASSAULT_MAX_DISTANCE_MAX),
            )
            .apply()
    }

    /** Макс. дистанция до монстра / цели штурма (targetPoint). */
    fun getAutoAssaultMaxDistanceTarget(): Int =
        prefs.getInt(
            KEY_AUTO_ASSAULT_MAX_DISTANCE_TARGET,
            getAutoAssaultMaxDistance(),
        ).coerceIn(AUTO_ASSAULT_MAX_DISTANCE_MIN, AUTO_ASSAULT_MAX_DISTANCE_MAX)

    fun setAutoAssaultMaxDistanceTarget(value: Int) {
        prefs.edit()
            .putInt(
                KEY_AUTO_ASSAULT_MAX_DISTANCE_TARGET,
                value.coerceIn(AUTO_ASSAULT_MAX_DISTANCE_MIN, AUTO_ASSAULT_MAX_DISTANCE_MAX),
            )
            .apply()
    }

    /** userId соалийцев, чьи штурмы разрешены (пусто = все). */
    fun getAutoAssaultAllowedMemberIds(): Set<String> =
        parseCsvStrings(prefs.getString(KEY_AUTO_ASSAULT_ALLOWED_MEMBER_IDS, ""))

    fun setAutoAssaultAllowedMemberIds(ids: Set<String>) {
        val csv = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")
        prefs.edit().putString(KEY_AUTO_ASSAULT_ALLOWED_MEMBER_IDS, csv).apply()
    }

    /**
     * Atomically commits overlay assault settings so [GameAutoAssaultBridge] reads the same values
     * immediately (individual [apply] calls can lag behind the next broadcast read).
     */
    fun commitAutoAssaultOverlayDraft(draft: AutoAssaultOverlayDraft): Boolean {
        val squadsCsv = draft.squads
            .filter { it in AUTO_ASSAULT_SQUAD_MIN..AUTO_ASSAULT_SQUAD_MAX }
            .sorted()
            .joinToString(",")
            .ifEmpty { "0" }
        val idsCsv = draft.allowedMemberIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
        val typesSanitized = draft.targetTypes
            .filter { it in AUTO_ASSAULT_TYPES_ALL }
            .toSet()
            .ifEmpty { AUTO_ASSAULT_TYPES_ALL }
        val distCreator = draft.maxDistanceCreator
            .coerceIn(AUTO_ASSAULT_MAX_DISTANCE_MIN, AUTO_ASSAULT_MAX_DISTANCE_MAX)
        val distTarget = draft.maxDistanceTarget
            .coerceIn(AUTO_ASSAULT_MAX_DISTANCE_MIN, AUTO_ASSAULT_MAX_DISTANCE_MAX)
        val distLegacy = maxOf(distCreator, distTarget)
        val editor = prefs.edit()
            .putString(KEY_AUTO_ASSAULT_SQUADS, squadsCsv)
            .putString(KEY_AUTO_ASSAULT_ALLOWED_MEMBER_IDS, idsCsv)
            .putStringSet(KEY_AUTO_ASSAULT_TARGET_TYPES, typesSanitized)
            .putInt(KEY_AUTO_ASSAULT_MAX_DISTANCE, distLegacy)
            .putInt(KEY_AUTO_ASSAULT_MAX_DISTANCE_CREATOR, distCreator)
            .putInt(KEY_AUTO_ASSAULT_MAX_DISTANCE_TARGET, distTarget)
            .putInt(
                KEY_AUTO_ASSAULT_LEVEL_MIN,
                draft.levelMin.coerceIn(0, AUTO_ASSAULT_LEVEL_MAX),
            )
            .putInt(
                KEY_AUTO_ASSAULT_LEVEL_MAX,
                draft.levelMax.coerceIn(0, AUTO_ASSAULT_LEVEL_MAX),
            )
            .putInt(
                KEY_AUTO_ASSAULT_MIN_REMAINING_SEC,
                draft.minRemainingSec.coerceIn(0, AUTO_ASSAULT_MIN_REMAINING_MAX_SEC),
            )
            .putInt(
                KEY_AUTO_ASSAULT_COOLDOWN_SEC,
                draft.cooldownSec.coerceIn(
                    AUTO_ASSAULT_COOLDOWN_MIN_SEC,
                    AUTO_ASSAULT_COOLDOWN_MAX_SEC,
                ),
            )
            .putInt(
                KEY_AUTO_ASSAULT_MAX_CONCURRENT,
                draft.maxConcurrent.coerceIn(0, AUTO_ASSAULT_MAX_CONCURRENT_CAP),
            )
            .putInt(
                KEY_AUTO_ASSAULT_DURATION_MIN,
                draft.durationMin.coerceIn(0, AUTO_ASSAULT_DURATION_MAX_MIN),
            )
        for (idx in AUTO_ASSAULT_SQUAD_MIN..AUTO_ASSAULT_SQUAD_MAX) {
            val min = draft.squadPowerMin.getOrElse(idx) { 0L }.coerceAtLeast(0L)
            val max = draft.squadPowerMax.getOrElse(idx) { AUTO_ASSAULT_POWER_MAX_DEFAULT }
                .coerceIn(0L, AUTO_ASSAULT_POWER_CEILING)
            editor.putLong(squadPowerMinKey(idx), min)
            editor.putLong(squadPowerMaxKey(idx), max)
        }
        return editor.commit()
    }

    private fun squadPowerMinKey(squadIndex: Int): String =
        "$KEY_AUTO_ASSAULT_SQUAD_POWER_MIN_PREFIX$squadIndex"

    private fun squadPowerMaxKey(squadIndex: Int): String =
        "$KEY_AUTO_ASSAULT_SQUAD_POWER_MAX_PREFIX$squadIndex"

    private fun parseCsvInts(raw: String?): List<Int> =
        raw?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()

    private fun parseCsvStrings(raw: String?): Set<String> =
        raw?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    /**
     * Путь к уже скачанному и проверенному APK патча, установка которого отложена до удаления
     * сток-игры. Переживает перезапуск процесса (OEM-киллеры фона), чтобы [OverlayControlScreen]
     * мог доустановить патч при возврате пользователя в приложение.
     */
    fun getPendingPatchApkPath(): String? =
        prefs.getString(KEY_PENDING_PATCH_APK_PATH, null)?.trim()?.ifEmpty { null }

    fun setPendingPatchApkPath(path: String?) {
        val edit = prefs.edit()
        if (path.isNullOrBlank()) {
            edit.remove(KEY_PENDING_PATCH_APK_PATH)
        } else {
            edit.putString(KEY_PENDING_PATCH_APK_PATH, path.trim())
        }
        edit.apply()
    }

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
        private const val KEY_AUTO_HELP_ENABLED = "auto_help_enabled"
        private const val KEY_AUTO_HELP_INTERVAL_SEC = "auto_help_interval_sec"
        const val AUTO_HELP_INTERVAL_DEFAULT_SEC = 30
        const val AUTO_HELP_INTERVAL_MIN_SEC = 5
        const val AUTO_HELP_INTERVAL_MAX_SEC = 600
        private const val KEY_AUTO_ASSAULT_ENABLED = "auto_assault_enabled"
        private const val KEY_AUTO_ASSAULT_SQUADS = "auto_assault_squads"
        private const val KEY_AUTO_ASSAULT_MAX_DISTANCE = "auto_assault_max_distance"
        private const val KEY_AUTO_ASSAULT_MAX_DISTANCE_CREATOR = "auto_assault_max_distance_creator"
        private const val KEY_AUTO_ASSAULT_MAX_DISTANCE_TARGET = "auto_assault_max_distance_target"
        private const val KEY_AUTO_ASSAULT_ALLOWED_MEMBER_IDS = "auto_assault_allowed_member_ids"
        private const val KEY_AUTO_ASSAULT_SQUAD_POWER_MIN_PREFIX = "auto_assault_squad_power_min_"
        private const val KEY_AUTO_ASSAULT_SQUAD_POWER_MAX_PREFIX = "auto_assault_squad_power_max_"
        const val AUTO_ASSAULT_SQUAD_MIN = 0
        const val AUTO_ASSAULT_SQUAD_MAX = 2
        const val AUTO_ASSAULT_MAX_DISTANCE_DEFAULT = 500
        const val AUTO_ASSAULT_MAX_DISTANCE_MIN = 1
        const val AUTO_ASSAULT_MAX_DISTANCE_MAX = 9999
        const val AUTO_ASSAULT_POWER_MAX_DEFAULT = 50_000_000L
        const val AUTO_ASSAULT_POWER_CEILING = 999_999_999L
        private const val KEY_AUTO_ASSAULT_DURATION_MIN = "auto_assault_duration_min"
        private const val KEY_AUTO_ASSAULT_DISABLE_AT = "auto_assault_disable_at"
        private const val KEY_AUTO_ASSAULT_TARGET_TYPES = "auto_assault_target_types"
        private const val KEY_AUTO_ASSAULT_LEVEL_MIN = "auto_assault_level_min"
        private const val KEY_AUTO_ASSAULT_LEVEL_MAX = "auto_assault_level_max"
        private const val KEY_AUTO_ASSAULT_MIN_REMAINING_SEC = "auto_assault_min_remaining_sec"
        private const val KEY_AUTO_ASSAULT_COOLDOWN_SEC = "auto_assault_cooldown_sec"
        private const val KEY_AUTO_ASSAULT_MAX_CONCURRENT = "auto_assault_max_concurrent"
        private const val KEY_AUTO_ASSAULT_JOIN_LOG = "auto_assault_join_log"
        private const val KEY_ALLIANCE_ROSTER_JSON = "alliance_roster_json"
        const val AUTO_ASSAULT_TYPE_MONSTER = "monster"
        const val AUTO_ASSAULT_TYPE_PLAYER = "player"
        const val AUTO_ASSAULT_TYPE_CITY = "city"
        val AUTO_ASSAULT_TYPES_ALL = setOf(
            AUTO_ASSAULT_TYPE_MONSTER,
            AUTO_ASSAULT_TYPE_PLAYER,
            AUTO_ASSAULT_TYPE_CITY,
        )
        const val AUTO_ASSAULT_LEVEL_MAX = 999
        const val AUTO_ASSAULT_DURATION_MAX_MIN = 1440
        const val AUTO_ASSAULT_MIN_REMAINING_DEFAULT_SEC = 5
        const val AUTO_ASSAULT_MIN_REMAINING_MAX_SEC = 600
        const val AUTO_ASSAULT_COOLDOWN_DEFAULT_SEC = 3
        const val AUTO_ASSAULT_COOLDOWN_MIN_SEC = 1
        const val AUTO_ASSAULT_COOLDOWN_MAX_SEC = 600
        const val AUTO_ASSAULT_MAX_CONCURRENT_CAP = 3
        const val AUTO_ASSAULT_JOIN_LOG_MAX = 10
        private const val KEY_PENDING_PATCH_APK_PATH = "pending_patch_apk_path"
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
