package com.lastasylum.alliance.data.chat

/** Chat line: `[TAG] nickname` when tag is set; otherwise plain nickname. */
fun chatSenderDisplayWithTag(teamTag: String?, username: String): String {
    val u = username.trim()
    val t = teamTag?.trim()?.takeIf { it.isNotEmpty() } ?: return u
    return "[$t] $u"
}
