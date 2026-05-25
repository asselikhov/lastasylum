package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.mergePreservingAttachments
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
                messages[i] = msg.mergePreservingAttachments(messages[i])
            } else {
                messages.add(msg)
            }
            return
        }
        val key = msg.stableKey()
        val i = messages.indexOfFirst {
            val otherId = it._id?.takeIf { oid -> oid.isNotBlank() }
            if (otherId != null) false else it.stableKey() == key
        }
        if (i >= 0) {
            messages[i] = msg.mergePreservingAttachments(messages[i])
        } else {
            messages.add(msg)
        }
    }

    fun prune() {
        if (messages.isEmpty()) return
        val cutoff = Instant.now().minus(messageTtlSeconds, ChronoUnit.SECONDS)
        messages.removeAll {
            // Keep service notices (e.g. "no room selected") visible; they are not part of TTL preview.
            if (OverlayStripNoticeIds.isNotice(it._id)) return@removeAll false
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
            .filter {
                OverlayStripNoticeIds.isNotice(it._id) ||
                    !OverlayChatTime.effectiveInstant(it, receivedAt).isBefore(cutoff)
            }
            .sortedBy { OverlayChatTime.effectiveInstant(it, receivedAt) }
            .takeLast(maxPreviewMessages)
    }

    fun markClientSend(sent: ChatMessage) {
        touchReceivedNow(sent)
    }

    /** Поднять TTL ленты для сообщения (повторная отправка / обновление карточки). */
    fun touchReceivedNow(msg: ChatMessage) {
        receivedAt[msg.stableKey()] = Instant.now()
    }

    fun mergeReceiveTimeline(msg: ChatMessage, @Suppress("UNUSED_PARAMETER") selfId: String?) {
        val key = msg.stableKey()
        val parsed = OverlayChatTime.parseInstant(msg.createdAt)
        val now = Instant.now()
        when {
            parsed == null -> receivedAt.putIfAbsent(key, now)
            else -> {
                val prev = receivedAt[key]
                receivedAt[key] = when {
                    prev == null -> parsed
                    else -> maxOf(prev, parsed)
                }
            }
        }
    }

    fun containsMessageId(messageId: String): Boolean {
        val id = messageId.trim()
        if (id.isEmpty()) return false
        return messages.any { it._id?.trim() == id }
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

    /**
     * Убрать карточку из ленты оверлея по тому же ключу, что [keyOf] в UI
     * ([_id] или [ChatMessage.stableKey]).
     */
    fun removeMessageWithKey(messageKey: String) {
        messages.removeAll { m ->
            val k = m._id?.takeIf { it.isNotBlank() } ?: m.stableKey()
            k == messageKey
        }
        receivedAt.remove(messageKey)
    }

    companion object {
        /** Keep overlay strip messages for 5 minutes by default. */
        const val DEFAULT_MESSAGE_TTL_SECONDS = 300L
        const val DEFAULT_BUFFER_CAP = 80
        /** Должно совпадать с лимитом в [CombatOverlayService] для ожидаемого числа карточек в ленте. */
        const val DEFAULT_MAX_PREVIEW = 13
    }
}
