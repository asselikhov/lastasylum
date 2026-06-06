package com.lastasylum.alliance.ui.util

import com.lastasylum.alliance.data.chat.ChatAllianceIds

/** Отображение номера сервера, например `#109`. */
fun formatServerLabel(serverNumber: Int?): String? {
    val n = serverNumber ?: return null
    if (n < 1) return null
    return "#$n"
}

/** Номер сервера из scope комнаты `srv:<n>`. */
fun parseServerNumberFromChatScope(allianceId: String?): Int? {
    if (!ChatAllianceIds.isServerScope(allianceId)) return null
    val n = allianceId!!.removePrefix(ChatAllianceIds.SERVER_PREFIX).toIntOrNull()
    return n?.takeIf { it >= 1 }
}

/**
 * Подпись вкладки серверной комнаты без `#` — решётка только в иконке вкладки.
 */
fun chatRoomTabLabelForServer(title: String, allianceId: String?): String {
    val stripped = title.trim().removePrefix("#").trim()
    if (stripped.isNotEmpty()) return stripped
    return parseServerNumberFromChatScope(allianceId)?.toString() ?: title.trim()
}

/** Строка отправителя: `#109 [TAG] nickname` (сервер перед тегом). */
fun chatSenderDisplayLine(
    teamTag: String?,
    username: String,
    serverNumber: Int? = null,
): String {
    val u = sanitizePublicDisplayName(username, fallback = "—")
    val prefix = buildList {
        formatServerLabel(serverNumber)?.let { add(it) }
        teamTag?.trim()?.takeIf { it.isNotEmpty() }?.let { add("[$it]") }
    }.joinToString(" ")
    return if (prefix.isEmpty()) u else "$prefix $u"
}

/** Префикс команды для заголовков: `#109 [TAG]`. */
fun teamTagWithServerPrefix(teamTag: String, serverNumber: Int?): String {
    val t = teamTag.trim()
    val server = formatServerLabel(serverNumber)
    return when {
        server != null && t.isNotEmpty() -> "$server [$t]"
        t.isNotEmpty() -> "[$t]"
        server != null -> server
        else -> ""
    }
}
