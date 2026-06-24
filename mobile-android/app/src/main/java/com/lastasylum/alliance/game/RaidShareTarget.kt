package com.lastasylum.alliance.game

import org.json.JSONObject

/**
 * Цель шаринга, перехваченная игровым патчем (панель «Поделиться в чат»).
 *
 * Канал доставки: Frida-бридж в процессе игры пишет JSON в приватный каталог игры,
 * JS читает его и шлёт broadcast [RaidShareBridge.ACTION_SHARE_TARGET] в SquadRelay
 * (см. `.tmp-tools/frida/map_fly_bridge.js`).
 *
 * `shareType=1` — это «зонтик» для объектов карты; тип кодируется в `nameKey`, поэтому
 * игровой хук резолвит читаемое имя через `Config[...]` и присылает [cat] + [name].
 */
data class RaidShareTarget(
    val seq: Long,
    val open: Boolean,
    val x: Int,
    val y: Int,
    val serverNumber: Int?,
    val shareType: Int,
    /** Уже локализованное имя цели (имя игрока, монстра, ресурса, владельца конвоя…). */
    val name: String?,
    val playerName: String?,
    /** Категория из игры: player / truck / SlgMonsterInfo / ResourceInfo / SlgRallyInfo / MechCity / … */
    val cat: String?,
    /** Уровень объекта (монстр/ресурс/ралли), если есть. */
    val lv: Int?,
    val secretTaskId: Int?,
    /** Грейд сундука: 3=SR, 4=SSR, 5=UR. */
    val grade: Int?,
    /** Звёзды сундука (secretLevel), 1..5. */
    val stars: Int?,
    /** Грейд конвоя (shareType=3), та же шкала, что у сундуков. */
    val qualityType: Int?,
) {
    val isChest: Boolean get() = secretTaskId != null || grade != null
    val isTruck: Boolean get() = cat == "truck"

    /** SR / SSR / UR по [grade], либо null. */
    fun gradeLabel(): String? = qualityToLabel(grade)

    /** SR / SSR / UR по [qualityType] (конвой), либо null. */
    fun qualityLabel(): String? = qualityToLabel(qualityType)

    /** Эмодзи-иконка категории для ленты чата. */
    fun categoryEmoji(): String = when {
        isChest -> "\uD83C\uDF81" // 🎁
        isTruck -> "\uD83D\uDE9A" // 🚚
        cat == "player" -> "\uD83C\uDFF0" // 🏰
        cat == "SlgMonsterInfo" -> "\uD83D\uDC79" // 👹
        cat == "ResourceInfo" -> "\uD83D\uDCE6" // 📦
        cat == "SlgRallyInfo" -> "\uD83D\uDC80" // 💀
        cat == "MechCity" -> "\uD83E\uDD16" // 🤖
        cat == "ActivityTempleBattleBuild" -> "\uD83C\uDFDB\uFE0F" // 🏛
        else -> "\uD83D\uDCCD" // 📍
    }

    /**
     * Богатый однострочный заголовок цели: `🎁 Сундук · UR ★★★ · Elenika29`,
     * `👹 Золотой вор · ур.5`, `🏰 [OBZH] 6apc`. Без координат и без префикса команды.
     */
    fun displayHeadline(): String {
        val title = when {
            isChest -> "\u0421\u0443\u043D\u0434\u0443\u043A" // Сундук
            isTruck -> "\u041A\u043E\u043D\u0432\u043E\u0439" // Конвой
            else -> cleanGameText(name) ?: cleanGameText(playerName) ?: "\u0426\u0435\u043B\u044C" // Цель
        }
        val meta = mutableListOf<String>()
        when {
            isChest -> {
                chestGradeStars()?.let { meta.add(it) }
                cleanGameText(playerName)?.let { meta.add(it) }
            }
            isTruck -> {
                qualityLabel()?.let { meta.add(it) }
                (cleanGameText(name) ?: cleanGameText(playerName))?.let { meta.add(it) }
            }
            else -> {
                val level = lv
                if (level != null && level > 0 && !title.contains("\u0443\u0440", ignoreCase = true)) {
                    meta.add("\u0443\u0440.$level") // ур.N
                }
            }
        }
        val head = if (meta.isEmpty()) title else title + " \u00B7 " + meta.joinToString(" \u00B7 ")
        return "${categoryEmoji()} $head"
    }

    /** `UR ★★★` для сундука, либо null. */
    private fun chestGradeStars(): String? {
        val g = gradeLabel()
        val s = stars
        val out = buildString {
            g?.let { append(it) }
            s?.let { v ->
                if (isNotEmpty()) append(" ")
                append("\u2605".repeat(v.coerceIn(1, 5)))
            }
        }
        return out.takeIf { it.isNotBlank() }
    }

    companion object {
        private fun qualityToLabel(q: Int?): String? = when (q) {
            3 -> "SR"
            4 -> "SSR"
            5 -> "UR"
            else -> null
        }

        /** Убирает игровые служебные маркеры (jamo-обёртки имён) и обрезает пробелы. */
        private fun cleanGameText(raw: String?): String? =
            raw?.replace("\u3151", "")
                ?.replace("\u3155", "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        fun fromJson(json: String?): RaidShareTarget? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                RaidShareTarget(
                    seq = o.optLong("seq", 0L),
                    open = o.optBoolean("open", false),
                    x = o.optInt("x", 0),
                    y = o.optInt("y", 0),
                    serverNumber = o.optInt("sid", 0).takeIf { it > 0 },
                    shareType = o.optInt("shareType", 0),
                    name = o.optString("name", "").trim().takeIf { it.isNotEmpty() },
                    playerName = o.optString("playerName", "").trim().takeIf { it.isNotEmpty() },
                    cat = o.optString("cat", "").trim().takeIf { it.isNotEmpty() },
                    lv = o.optInt("lv", 0).takeIf { it > 0 },
                    secretTaskId = o.optInt("secretTaskId", 0).takeIf { it > 0 },
                    grade = o.optInt("grade", 0).takeIf { it > 0 },
                    stars = o.optInt("stars", 0).takeIf { it > 0 },
                    qualityType = o.optInt("qualityType", 0).takeIf { it > 0 },
                )
            }.getOrNull()
        }
    }
}
