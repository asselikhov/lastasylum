package com.lastasylum.alliance.data.telemetry

enum class LatencySpanType(val wire: String) {
    ChatSendToSocket("chat_send_to_socket"),
    ChatSendToHttpAck("chat_send_to_http_ack"),
    /** Composer tap → optimistic row visible in chat list. */
    ChatSendToOptimisticPaint("chat_send_to_optimistic_paint"),
    ChatReadReceipt("chat_read_receipt"),
    ForumSendToSocket("forum_send_to_socket"),
    OverlayStripIngest("overlay_strip_ingest"),
    /** Quick command tap → optimistic row / strip paint (overlay raid). */
    OverlayRaidQuickCommandSend("overlay_raid_quick_send"),
    /** Peer message socket delivery → visible in active chat list. */
    ChatPeerMessageVisible("chat_peer_message_visible"),
}

data class LatencySnapshot(
    val byType: Map<LatencySpanType, LatencyStats>,
    val sampleCount: Int,
    val windowStartedAtMs: Long,
)

data class LatencyStats(
    val count: Int,
    val p50Ms: Long,
    val p95Ms: Long,
    val p99Ms: Long,
)
