package com.lastasylum.alliance.game

/**
 * Пункт сбора альянса из игры → SquadRelay.
 * Frida-бридж пишет `squadrelay_alliance_rally.json` и шлёт broadcast с JSON `{"x","y","sid"}`.
 */
object AllianceRallyBridge {
    const val ACTION_ALLIANCE_RALLY = "com.lastasylum.alliance.action.ALLIANCE_RALLY"
    const val EXTRA_PAYLOAD = "payload"
}

data class AllianceRallyPoint(
    val x: Int,
    val y: Int,
    val serverNumber: Int,
) {
    fun isValid(): Boolean = x > 0 && y > 0 && serverNumber > 0

    companion object {
        fun parse(json: String?, serverHint: Int? = null): AllianceRallyPoint? {
            val raw = json?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return runCatching {
                val obj = org.json.JSONObject(raw)
                val x = obj.optInt("x", -1)
                val y = obj.optInt("y", -1)
                val sid = when {
                    obj.has("sid") -> obj.optInt("sid", -1)
                    obj.has("server") -> obj.optInt("server", -1)
                    else -> -1
                }
                val point = AllianceRallyPoint(x, y, sid)
                if (point.isValid()) return@runCatching point
                val hint = serverHint?.takeIf { it > 0 } ?: return@runCatching null
                if (point.x > 0 && point.y > 0) point.copy(serverNumber = hint) else null
            }.getOrNull()
        }
    }
}
