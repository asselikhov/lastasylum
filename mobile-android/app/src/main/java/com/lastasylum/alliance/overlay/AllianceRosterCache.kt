package com.lastasylum.alliance.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Один участник альянса из игрового моста (Data.AllianceData.member.memberDic). */
data class AllianceMember(
    val id: String,
    val name: String,
    val power: Long,
    val level: Int,
    val castle: Int,
    val rank: Int,
    val kills: Long,
    val x: Int,
    val y: Int,
    val sid: Int,
    /** epoch ms последнего выхода (0 — неизвестно). */
    val logoutMs: Long,
) {
    val hasCoords: Boolean get() = x > 0 && y > 0
}

/**
 * Кэш игрового ростера альянса (его шлёт патч-мост). Используется выбором соалийцев
 * в авто-штурме и окном «Участники альянса». Никнеймы совпадают с именами в игре.
 */
internal object AllianceRosterCache {
    private val _members = MutableStateFlow<List<AllianceMember>>(emptyList())
    val members: StateFlow<List<AllianceMember>> = _members.asStateFlow()

    fun peek(): List<AllianceMember> = _members.value

    fun update(list: List<AllianceMember>) {
        _members.value = list
    }

    /** Парсит JSON-массив `[{id,name,power,level,castle,rank,kills,x,y,sid,logout}]`. */
    fun parse(json: String?): List<AllianceMember> {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<AllianceMember>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val name = o.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                out.add(
                    AllianceMember(
                        id = id,
                        name = name,
                        power = o.optLong("power", 0L),
                        level = o.optInt("level", 0),
                        castle = o.optInt("castle", 0),
                        rank = o.optInt("rank", 0),
                        kills = o.optLong("kills", 0L),
                        x = o.optInt("x", 0),
                        y = o.optInt("y", 0),
                        sid = o.optInt("sid", 0),
                        logoutMs = o.optLong("logout", 0L),
                    ),
                )
            }
            out
        }.getOrDefault(emptyList())
    }
}
