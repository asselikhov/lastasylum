package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Буфер сообщений для короткой ленты оверлея: upsert, окно по времени, лимит размера.
 * Карта «время прихода» используется и панелью истории ([OverlayChatTime.effectiveInstant]).
 */
class OverlayChatStripBuffer(
    /** Сколько секунд сообщение остаётся в превью ленты (после [OverlayChatTime.effectiveInstant]). */
    private val messageTtlSeconds: Long = DEFAULT_MESSAGE_TTL_SECONDS,
    private val bufferCap: Int = DEFAULT_BUFFER_CAP,
    private val maxPreviewMessages: Int = DEFAULT_MAX_PREVIEW,
) {
    private val messages = mutableListOf<ChatMessage>()
    private val receivedAt = mutableMapOf<String, Instant>()

    /** Та же структура, что раньше в сервисе — для [OverlayChatHistoryPanel]. */
    fun receivedAtMap(): MutableMap<String, Instant> = receivedAt

    fun clear() {
        messages.clear()
        receivedAt.clear()
    }

    fun upsert(msg: ChatMessage) {
        val id = msg._id?.takeIf { it.isNotBlank() }
        if (id != null) {
            val i = messages.indexOfFirst { it._id == id }
            if (i >= 0) {
                messages[i] = msg
            } else {
                messages.add(msg)
            }
        } else {
            messages.add(msg)
        }
    }

    fun prune() {
        if (messages.isEmpty()) return
        val cutoff = Instant.now().minus(messageTtlSeconds, ChronoUnit.SECONDS)
        messages.removeAll {
            OverlayChatTime.effectiveInstant(it, receivedAt).isBefore(cutoff)
        }
        messages.sortBy { OverlayChatTime.effectiveInstant(it, receivedAt) }
        while (messages.size > bufferCap) {
            messages.removeAt(0)
        }
    }

    fun visibleForPreview(): List<ChatMessage> {
        val cutoff = Instant.now().minus(messageTtlSeconds, ChronoUnit.SECONDS)
        return messages
            .filter { !OverlayChatTime.effectiveInstant(it, receivedAt).isBefore(cutoff) }
            .sortedBy { OverlayChatTime.effectiveInstant(it, receivedAt) }
            .takeLast(maxPreviewMessages)
    }

    fun markClientSend(sent: ChatMessage) {
        receivedAt[sent.stableKey()] = Instant.now()
    }

    fun mergeReceiveTimeline(msg: ChatMessage, selfId: String?) {
        val key = msg.stableKey()
        val parsed = OverlayChatTime.parseInstant(msg.createdAt)
        val cutoff = Instant.now().minus(messageTtlSeconds, ChronoUnit.SECONDS)
        when {
            parsed == null -> receivedAt.putIfAbsent(key, Instant.now())
            parsed.isBefore(cutoff) &&
                selfId != null &&
                msg.senderId == selfId -> {
                receivedAt[key] = Instant.now()
            }
            else -> receivedAt.putIfAbsent(key, parsed)
        }
    }

    fun seedFromHistory(loaded: List<ChatMessage>) {
        clear()
        loaded.forEach { m ->
            upsert(m)
            when (val p = OverlayChatTime.parseInstant(m.createdAt)) {
                null -> receivedAt.putIfAbsent(m.stableKey(), Instant.now())
                else -> receivedAt[m.stableKey()] = p
            }
        }
    }

    companion object {
        const val DEFAULT_MESSAGE_TTL_SECONDS = 10L
        const val DEFAULT_BUFFER_CAP = 80
        const val DEFAULT_MAX_PREVIEW = 5
    }
}
