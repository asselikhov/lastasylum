package com.lastasylum.alliance.game

import org.json.JSONObject

/**
 * Цель шаринга, перехваченная игровым патчем (панель «Поделиться в чат»).
 *
 * Канал доставки: Frida-бридж в процессе игры пишет JSON в приватный каталог игры,
 * JS читает его и шлёт broadcast [RaidShareBridge.ACTION_SHARE_TARGET] в SquadRelay
 * (см. `.tmp-tools/frida/map_fly_bridge.js`).
 */
data class RaidShareTarget(
    val seq: Long,
    val open: Boolean,
    val x: Int,
    val y: Int,
    val serverNumber: Int?,
    val shareType: Int,
    val name: String?,
    val playerName: String?,
    val secretTaskId: Int?,
    /** Грейд сундука: 3=SR, 4=SSR, 5=UR. */
    val grade: Int?,
    /** Звёзды сундука (secretLevel), 1..5. */
    val stars: Int?,
) {
    val isChest: Boolean get() = secretTaskId != null || grade != null

    /** SR / SSR / UR по [grade], либо null. */
    fun gradeLabel(): String? = when (grade) {
        3 -> "SR"
        4 -> "SSR"
        5 -> "UR"
        else -> null
    }

    companion object {
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
                    secretTaskId = o.optInt("secretTaskId", 0).takeIf { it > 0 },
                    grade = o.optInt("grade", 0).takeIf { it > 0 },
                    stars = o.optInt("stars", 0).takeIf { it > 0 },
                )
            }.getOrNull()
        }
    }
}
