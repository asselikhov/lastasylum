package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.mergeIncomingChatUpdate
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
    /**
     * null — вне игровой сессии, чужие карточки не показываем;
     * [resetVisibleSession] — только сообщения, принятые после входа в матч.
     */
    private var visibleSince: Instant? = null

    /** Та же структура, что раньше в сервисе — для [OverlayChatHistoryPanel]. */
    fun receivedAtMap(): MutableMap<String, Instant> = receivedAt

    /** Новая игровая сессия оверлея — сбросить накопленное «офлайн» и показывать только новый трафик. */
    fun resetVisibleSession() {
        visibleSince = Instant.now()
    }

    fun clear() {
        messages.clear()
        receivedAt.clear()
        visibleSince = null
    }

    fun upsert(msg: ChatMessage) {
        val id = msg._id?.takeIf { it.isNotBlank() }
        if (id != null) {
            val i = messages.indexOfFirst { it._id == id }
            if (i >= 0) {
                messages[i] = msg.mergeIncomingChatUpdate(messages[i])
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
            messages[i] = msg.mergeIncomingChatUpdate(messages[i])
        } else {
            messages.add(msg)
        }
    }

    fun prune() {
        if (messages.isEmpty()) return
        val ttlCutoff = Instant.now().minus(messageTtlSeconds, ChronoUnit.SECONDS)
        val sessionCutoff = visibleSince
        messages.removeAll {
            // Keep service notices (e.g. "no room selected") visible; they are not part of TTL preview.
            if (OverlayStripNoticeIds.isNotice(it._id)) return@removeAll false
            if (sessionCutoff == null) return@removeAll true
            val t = OverlayChatTime.effectiveInstant(it, receivedAt)
            t.isBefore(ttlCutoff) || t.isBefore(sessionCutoff)
        }
        messages.sortBy { OverlayChatTime.effectiveInstant(it, receivedAt) }
        while (messages.size > bufferCap) {
            messages.removeAt(0)
        }
    }

    fun visibleForPreview(): List<ChatMessage> {
        val ttlCutoff = Instant.now().minus(messageTtlSeconds, ChronoUnit.SECONDS)
        val sessionCutoff = visibleSince
        return messages
            .filter {
                if (OverlayStripNoticeIds.isNotice(it._id)) return@filter true
                if (sessionCutoff == null) return@filter false
                val t = OverlayChatTime.effectiveInstant(it, receivedAt)
                !t.isBefore(ttlCutoff) && !t.isBefore(sessionCutoff)
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
        val now = Instant.now()
        // В ленте оверлея важно «когда увидели в этой игровой сессии», не createdAt с сервера.
        receivedAt[key] = now
    }

    fun containsMessageId(messageId: String): Boolean {
        val id = messageId.trim()
        if (id.isEmpty()) return false
        return messages.any { it._id?.trim() == id }
    }

    fun seedFromHistory(loaded: List<ChatMessage>) {
        clear()
        visibleSince = Instant.EPOCH
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
