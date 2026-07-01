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
        fun parse(json: String?): AllianceRallyPoint? {
            val raw = json?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return runCatching {
                val obj = org.json.JSONObject(raw)
                val x = obj.optInt("x", -1)
                val y = obj.optInt("y", -1)
                val sid = obj.optInt("sid", obj.optInt("server", -1))
                AllianceRallyPoint(x, y, sid).takeIf { it.isValid() }
            }.getOrNull()
        }
    }
}
