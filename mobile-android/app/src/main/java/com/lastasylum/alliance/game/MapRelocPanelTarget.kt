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
    /** Низ игровой панели (px от верха экрана) — fallback для позиции кнопки «Маршрут». */
    val panelBottomPx: Int?,
    /** Прямоугольник игровой кнопки «Перемещение» в px от верхнего левого угла экрана. */
    val relocBtnLeftPx: Int? = null,
    val relocBtnTopPx: Int? = null,
    val relocBtnRightPx: Int? = null,
    val relocBtnBottomPx: Int? = null,
    val relocBtnWidthPx: Int? = null,
    val relocBtnHeightPx: Int? = null,
    /** RGB 0xRRGGBB (без alpha) цвета спрайта игровой кнопки. */
    val relocBtnColorRgb: Int? = null,
) {
    val hasCoords: Boolean get() = x > 0 && y > 0

    fun sidOrFallback(fallbackSid: Int?): Int =
        sid.takeIf { it > 0 } ?: (fallbackSid?.takeIf { it > 0 } ?: 0)

    fun isValidWithSid(fallbackSid: Int?): Boolean =
        hasCoords && sidOrFallback(fallbackSid) > 0

    fun withResolvedSid(fallbackSid: Int?): MapRelocPanelTarget {
        val resolved = sidOrFallback(fallbackSid)
        return if (resolved == sid) this else copy(sid = resolved)
    }

    fun hasRelocButtonAnchor(): Boolean =
        relocBtnRightPx != null && relocBtnTopPx != null &&
            relocBtnWidthPx != null && relocBtnHeightPx != null &&
            relocBtnWidthPx > 0 && relocBtnHeightPx > 0

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
                    relocBtnLeftPx = o.optInt("relocBtnLeftPx", 0).takeIf { it > 0 },
                    relocBtnTopPx = o.optInt("relocBtnTopPx", 0).takeIf { it > 0 },
                    relocBtnRightPx = o.optInt("relocBtnRightPx", 0).takeIf { it > 0 },
                    relocBtnBottomPx = o.optInt("relocBtnBottomPx", 0).takeIf { it > 0 },
                    relocBtnWidthPx = o.optInt("relocBtnWidthPx", 0).takeIf { it > 0 },
                    relocBtnHeightPx = o.optInt("relocBtnHeightPx", 0).takeIf { it > 0 },
                    relocBtnColorRgb = o.optInt("relocBtnColorArgb", 0).takeIf { it > 0 }
                        ?: o.optInt("relocBtnColorRgb", 0).takeIf { it > 0 },
                )
            }.getOrNull()
        }
    }
}
