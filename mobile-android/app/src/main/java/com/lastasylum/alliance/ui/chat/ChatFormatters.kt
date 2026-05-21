package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.ui.util.chatDayKeyMsk
import com.lastasylum.alliance.ui.util.formatChatDaySeparatorMsk
import com.lastasylum.alliance.ui.util.formatChatTimeMsk

/** Calendar day key `yyyy-MM-dd` in Moscow time, or null. */
fun chatDayKey(createdAt: String?): String? = chatDayKeyMsk(createdAt)

/** Chip label for a day separator (e.g. «17 апреля»), Moscow calendar. */
fun formatChatDaySeparator(createdAt: String?): String = formatChatDaySeparatorMsk(createdAt)

/** Short time for chat bubbles (`HH:mm`, Moscow). */
fun formatChatTime(createdAt: String?): String = formatChatTimeMsk(createdAt)
