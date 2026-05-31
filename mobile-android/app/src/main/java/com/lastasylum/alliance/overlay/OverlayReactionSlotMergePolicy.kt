package com.lastasylum.alliance.overlay

internal data class OverlayReactionMergeableHead(
    val fromUserId: String,
    val broadcast: Boolean,
    val createdAtMs: Long,
)

internal object OverlayReactionSlotMergePolicy {
    const val MERGE_WINDOW_MS = 2_000L

    fun canMergeIntoHead(
        head: OverlayReactionMergeableHead,
        incoming: OverlayReactionBurstRequest,
        nowMs: Long,
    ): Boolean {
        val sameSender = head.fromUserId.trim() == incoming.fromUserId.trim() &&
            head.fromUserId.isNotBlank()
        if (!sameSender || head.broadcast != incoming.broadcast) return false
        return nowMs - head.createdAtMs <= MERGE_WINDOW_MS
    }
}
