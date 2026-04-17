package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Стабильный ключ для карт «время прихода» без серверного id. */
fun ChatMessage.stableKey(): String =
    (_id?.takeIf { it.isNotBlank() })
        ?: "${senderId}:${createdAt.orEmpty()}:${text.hashCode()}"

/** Разбор ISO-времени сообщений чата и отображение в оверлее. */
object OverlayChatTime {
    private val clockFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun parseInstant(createdAt: String?): Instant? {
        if (createdAt.isNullOrBlank()) return null
        val s = createdAt.trim()
        return try {
            Instant.parse(s)
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    /**
     * Время для сортировки и «5 минут»: сначала серверное [ChatMessage.createdAt],
     * иначе время первого прихода в оверлей из [receivedAt].
     */
    fun effectiveInstant(msg: ChatMessage, receivedAt: Map<String, Instant>): Instant {
        parseInstant(msg.createdAt)?.let { return it }
        val key = msg.stableKey()
        return receivedAt[key] ?: Instant.EPOCH
    }

    fun formatClock(instant: Instant): String =
        clockFormatter.withZone(ZoneId.systemDefault()).format(instant)
}
