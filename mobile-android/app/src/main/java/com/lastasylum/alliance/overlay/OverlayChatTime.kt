package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.ui.util.APP_DISPLAY_ZONE
import com.lastasylum.alliance.ui.util.parseIsoInstant
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Стабильный ключ для карт «время прихода» без серверного id. */
fun ChatMessage.stableKey(): String =
    (_id?.takeIf { it.isNotBlank() })
        ?: run {
            val att =
                attachments.joinToString("|") { "${it.kind}:${it.url}" }.hashCode()
            "${senderId}:${createdAt.orEmpty()}:${text.hashCode()}:$att"
        }

/** Разбор ISO-времени сообщений чата и отображение в оверлее (МСК). */
object OverlayChatTime {
    private val clockFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru"))
            .withZone(APP_DISPLAY_ZONE)

    fun parseInstant(createdAt: String?): Instant? = parseIsoInstant(createdAt)

    /**
     * Время для сортировки и TTL ленты: [max] серверного [ChatMessage.createdAt]
     * и времени прихода в оверлей из [receivedAt], чтобы локальная отметка (например после POST)
     * не игнорировалась при «кривой» дате с сервера.
     */
    fun effectiveInstant(msg: ChatMessage, receivedAt: Map<String, Instant>): Instant {
        val server = parseInstant(msg.createdAt)
        val client = receivedAt[msg.stableKey()]
        if (server == null) return client ?: Instant.EPOCH
        if (client == null) return server
        return maxOf(server, client)
    }

    fun formatClock(instant: Instant): String = clockFormatter.format(instant)
}
