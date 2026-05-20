package com.lastasylum.alliance.ui.util

/** Отображение номера сервера, например `#109`. */
fun formatServerLabel(serverNumber: Int?): String? {
    val n = serverNumber ?: return null
    if (n < 1) return null
    return "#$n"
}

/** Строка отправителя: `#109 [TAG] nickname` (сервер перед тегом). */
fun chatSenderDisplayLine(
    teamTag: String?,
    username: String,
    serverNumber: Int? = null,
): String {
    val u = username.trim().ifBlank { "—" }
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
