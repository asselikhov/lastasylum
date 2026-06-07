package com.lastasylum.alliance.data.chat.outbox

enum class OutboxSendState(val wire: String) {
    Pending("pending"),
    Sending("sending"),
    Sent("sent"),
    Failed("failed"),
    ;

    companion object {
        fun fromWire(value: String): OutboxSendState =
            entries.firstOrNull { it.wire == value.trim() } ?: Pending
    }
}

enum class OutboxSendSource(val wire: String) {
    ChatUi("chat_ui"),
    OverlayRaid("overlay_raid"),
}

data class OutboxEntry(
    val clientMessageId: String,
    val pendingMessageId: String,
    val roomId: String,
    val text: String,
    val replyToMessageId: String?,
    val attachments: List<String>?,
    val excavationAlert: Boolean,
    /** Overlay Push tab: [com.lastasylum.alliance.gameevents.GameEventCatalog] id for FCM. */
    val gameEventAlert: String? = null,
    val source: OutboxSendSource,
    val state: OutboxSendState,
    val attempts: Int,
    val createdAtMs: Long,
    val lastError: String?,
)
