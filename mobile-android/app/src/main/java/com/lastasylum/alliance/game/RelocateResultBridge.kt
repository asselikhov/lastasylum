package com.lastasylum.alliance.game

import org.json.JSONObject

/** Результат команды перемещения из игрового моста (ok/error). */
object RelocateResultBridge {
    const val ACTION_RELOCATE_RESULT = "com.lastasylum.alliance.action.RELOCATE_RESULT"
    const val EXTRA_PAYLOAD = "payload"
}

data class RelocateResult(
    val ok: Boolean,
    val mode: String,
    val error: String?,
) {
    companion object {
        fun parse(json: String?): RelocateResult? {
            val raw = json?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return runCatching {
                val obj = JSONObject(raw)
                RelocateResult(
                    ok = obj.optBoolean("ok", false),
                    mode = obj.optString("mode", "").trim(),
                    error = obj.optString("error", "").trim().ifEmpty { null },
                )
            }.getOrNull()
        }
    }
}
