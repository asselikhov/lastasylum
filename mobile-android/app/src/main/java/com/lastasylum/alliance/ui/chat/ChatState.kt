package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto

enum class ChatVoicePhase {
    Idle,
    Listening,
    Sending,
}

data class ChatSendFailure(
    val messageText: String,
    val replyToMessageId: String?,
    val errorMessage: String,
)

data class ChatState(
    val isLoading: Boolean = false,
    val isRoomsLoading: Boolean = true,
    /** Full team name + tag set in profile — required to post in «Межсерв». */
    val hasTeamProfileForGlobalChat: Boolean = false,
    val rooms: List<ChatRoomDto> = emptyList(),
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
    val sendFailure: ChatSendFailure? = null,
    /** Wire keys for sticker packs the user may send (e.g. zlobyaka). */
    val enabledStickerPackKeys: Set<String> = emptySet(),
)
