package com.lastasylum.alliance.push

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import com.lastasylum.alliance.ui.chat.ChatSenderLineColors
import com.lastasylum.alliance.ui.util.chatSenderDisplayLine
import com.lastasylum.alliance.ui.util.formatServerLabel

/** Incoming chat header colors (#109 [TAG] nickname) — matches [ChatBubbleAuthorHeader]. */
object PushNotificationSenderLineSpans {
    fun build(
        teamTag: String?,
        username: String,
        serverNumber: Int?,
        squadRole: String? = null,
    ): CharSequence {
        val line = chatSenderDisplayLine(teamTag, username, serverNumber)
        val spannable = SpannableString(line)
        val bold = StyleSpan(Typeface.BOLD)
        var searchFrom = 0
        formatServerLabel(serverNumber)?.let { server ->
            val start = line.indexOf(server, searchFrom)
            if (start >= 0) {
                val end = start + server.length
                spannable.setSpan(
                    ForegroundColorSpan(ChatSenderLineColors.serverColorArgb()),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                spannable.setSpan(bold, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                searchFrom = end
            }
        }
        val rawTag = teamTag?.trim()?.removePrefix("[")?.removeSuffix("]")?.trim().orEmpty()
        if (rawTag.isNotEmpty()) {
            val tagLabel = "[$rawTag]"
            val start = line.indexOf(tagLabel, searchFrom)
            if (start >= 0) {
                val end = start + tagLabel.length
                spannable.setSpan(
                    ForegroundColorSpan(ChatSenderLineColors.tagColorArgb()),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                spannable.setSpan(bold, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                searchFrom = end
            }
        }
        val nick = username.trim().ifBlank { "—" }
        val nickStart = line.indexOf(nick, searchFrom).coerceAtLeast(0)
        if (nickStart < line.length) {
            spannable.setSpan(
                ForegroundColorSpan(ChatSenderLineColors.nicknameColorArgb(squadRole)),
                nickStart,
                line.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            spannable.setSpan(bold, nickStart, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }
}
