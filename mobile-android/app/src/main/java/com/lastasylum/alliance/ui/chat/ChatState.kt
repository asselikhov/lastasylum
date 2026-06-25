package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto

enum class ChatVoicePhase {
    Idle,
    Listening,
    Sending,
}

data class ChatSendFailure(
    val messageText: String,
    val replyToMessageId: String?,
    val errorMessage: String,
    /**
     * Идентификатор исходного outbox-сообщения. Если задан — «Повторить» переотправляет ту же
     * строку (тот же [clientMessageId]), что идемпотентно на сервере и не создаёт дубликат у пира.
     */
    val clientMessageId: String? = null,
    val pendingMessageId: String? = null,
)

data class ChatState(
    val isLoading: Boolean = false,
    val isRoomsLoading: Boolean = true,
    /** Full team name + tag set in profile — required to post in «Межсерв». */
    val hasTeamProfileForGlobalChat: Boolean = false,
    val rooms: List<ChatRoomDto> = emptyList(),
    /** Bottom nav «Чат» badge; recomputed when rooms or read cursors change. */
    val tabUnreadBadge: Int = 0,
    val selectedRoomId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null,
    val currentUserId: String = "",
    /** App alliance role (admin tab); not squad rank. */
    val currentUserRole: String = "",
    val isAppAdmin: Boolean = false,
    /** Squad rank R1–R5 on current player team; used for team chat moderation. */
    val playerTeamSquadRole: String? = null,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val isSending: Boolean = false,
    val replyToMessage: ChatMessage? = null,
    val editingMessage: ChatMessage? = null,
    val activeActionMessageId: String? = null,
    val confirmDeleteMessageId: String? = null,
    /** Multi-select for bulk delete (long-press on a deletable message, then tap more). */
    val selectedMessageIds: Set<String> = emptySet(),
    val confirmBulkDelete: Boolean = false,
    val isDeletingSelection: Boolean = false,
    val deletingMessageId: String? = null,
    val newestMessageKey: String? = null,
    /** Increments after a successful own send so the list scrolls to the latest message. */
    val scrollToLatestNonce: Long = 0L,
    /** Scroll list to this message id (quote jump); consumed by [ChatScreen]. */
    val scrollToMessageId: String? = null,
    /** Brief highlight after jumping to a quoted message. */
    val highlightMessageId: String? = null,
    /** One-shot toast/snackbar text (e.g. quote not found). */
    val transientNotice: String? = null,
    /** Pin/unpin REST in flight — disables sheet actions. */
    val pinInFlight: Boolean = false,
    /** Pinned bar preview (may cycle through local pin history). */
    val pinBarPreview: PinnedMessagePreviewDto? = null,
    /** Total pins in local history when > 1 (Telegram-style badge). */
    val pinHistoryCount: Int = 0,
    /** Server-synced pin list for the active room (newest first). */
    val pinnedMessages: List<com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto> = emptyList(),
    /** Pin bar hidden via dismiss — show compact chip instead. */
    val isPinBarDismissed: Boolean = false,
    val sendFailure: ChatSendFailure? = null,
    /** Wire keys for sticker packs the user may send (e.g. zlobyaka). */
    val enabledStickerPackKeys: Set<String> = emptySet(),
)
