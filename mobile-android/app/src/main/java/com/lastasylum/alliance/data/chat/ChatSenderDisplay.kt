package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.ui.util.chatSenderDisplayLine

/** Chat line: `#109 [TAG] nickname` when server/tag set. */
fun chatSenderDisplayWithTag(
    teamTag: String?,
    username: String,
    serverNumber: Int? = null,
): String = chatSenderDisplayLine(teamTag, username, serverNumber)
