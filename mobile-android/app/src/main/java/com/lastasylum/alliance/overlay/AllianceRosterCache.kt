package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Кэш игрового ростера альянса (из патч-моста), используется для выбора соалийцев
 * в настройках авто-штурма. Никнеймы здесь совпадают с именами создателей штурмов в игре,
 * поэтому фильтр авто-вступления по именам сопоставляется напрямую.
 */
internal object AllianceRosterCache {
    private val _members = MutableStateFlow<List<PlayerTeamMemberDto>>(emptyList())
    val members: StateFlow<List<PlayerTeamMemberDto>> = _members.asStateFlow()

    fun peek(): List<PlayerTeamMemberDto> = _members.value

    fun update(list: List<PlayerTeamMemberDto>) {
        _members.value = list
    }

    /** Парсит JSON-массив `[{id,name,power,level,rank}]` из игрового моста в список членов. */
    fun parse(json: String?): List<PlayerTeamMemberDto> {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<PlayerTeamMemberDto>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val name = o.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                out.add(PlayerTeamMemberDto(userId = id, username = name, isLeader = false))
            }
            out.sortedBy { it.username.lowercase() }
        }.getOrDefault(emptyList())
    }
}
