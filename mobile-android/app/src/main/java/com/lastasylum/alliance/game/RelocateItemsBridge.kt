package com.lastasylum.alliance.game

/**
 * Остаток предметов перемещения в инвентаре игрока (из `Data.ItemData.itemsCount`).
 * Frida-бридж шлёт broadcast с JSON `{"direct":N,"alliance":M,"random":R}`.
 */
object RelocateItemsBridge {
    const val ACTION_RELOCATE_ITEMS = "com.lastasylum.alliance.action.RELOCATE_ITEMS"
    const val EXTRA_PAYLOAD = "payload"
}

data class RelocateItemCounts(
    val direct: Int,
    val alliance: Int,
    val random: Int,
) {
    fun directAvailable(): Boolean = direct > 0

    fun allianceAvailable(): Boolean = alliance > 0

    fun randomAvailable(): Boolean = random > 0

    companion object {
        fun parse(json: String?): RelocateItemCounts? {
            val raw = json?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return runCatching {
                val obj = org.json.JSONObject(raw)
                RelocateItemCounts(
                    direct = obj.optInt("direct", -1).coerceAtLeast(0),
                    alliance = obj.optInt("alliance", -1).coerceAtLeast(0),
                    random = obj.optInt("random", -1).coerceAtLeast(0),
                )
            }.getOrNull()
        }
    }
}
