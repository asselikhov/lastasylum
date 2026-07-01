package com.lastasylum.alliance.game

import org.json.JSONObject

/**
 * Состояние игровой панели «Перемещение» при тапе по пустой клетке карты.
 * Frida-мост шлёт broadcast через [MapRelocPanelBridge].
 */
data class MapRelocPanelTarget(
    val seq: Long,
    val open: Boolean,
    val x: Int,
    val y: Int,
    val sid: Int,
    /** Низ игровой панели (px от верха экрана) — для позиции кнопки «Маршрут». */
    val panelBottomPx: Int?,
) {
    val isValid: Boolean get() = x > 0 && y > 0 && sid > 0

    companion object {
        fun fromJson(json: String?): MapRelocPanelTarget? {
            val raw = json?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return runCatching {
                val o = JSONObject(raw)
                MapRelocPanelTarget(
                    seq = o.optLong("seq", 0L),
                    open = o.optBoolean("open", false),
                    x = o.optInt("x", 0),
                    y = o.optInt("y", 0),
                    sid = o.optInt("sid", 0),
                    panelBottomPx = o.optInt("panelBottomPx", 0).takeIf { it > 0 },
                )
            }.getOrNull()
        }
    }
}
