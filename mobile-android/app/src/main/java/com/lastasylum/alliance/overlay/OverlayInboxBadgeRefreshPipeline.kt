package com.lastasylum.alliance.overlay

/**
 * Queues partial overlay inbox badge refreshes while a full HUD refresh is in flight.
 * [CombatOverlayService] owns IO/emit; this class only coalesces pending flags.
 */
internal data class OverlayInboxCoalescedRequest(
    val includeHub: Boolean = false,
    val includeNews: Boolean = false,
    val includeForum: Boolean = false,
    val forceHubReconcile: Boolean = false,
    val forceNewsReconcile: Boolean = false,
    val forceForumReconcile: Boolean = false,
    val includeReactionLog: Boolean = false,
) {
    fun mergeWith(
        includeHub: Boolean,
        includeNews: Boolean,
        includeForum: Boolean,
        forceHubReconcile: Boolean,
        forceNewsReconcile: Boolean,
        forceForumReconcile: Boolean,
        includeReactionLog: Boolean,
    ): OverlayInboxCoalescedRequest {
        return OverlayInboxCoalescedRequest(
            includeHub = this.includeHub || includeHub,
            includeNews = this.includeNews || includeNews,
            includeForum = this.includeForum || includeForum,
            forceHubReconcile = this.forceHubReconcile || forceHubReconcile,
            forceNewsReconcile = this.forceNewsReconcile || forceNewsReconcile,
            forceForumReconcile = this.forceForumReconcile || forceForumReconcile,
            includeReactionLog = this.includeReactionLog || includeReactionLog,
        )
    }
}

internal class OverlayInboxBadgeRefreshPipeline {
    private var pending: OverlayInboxCoalescedRequest? = null

    fun queue(
        includeHub: Boolean,
        includeNews: Boolean,
        includeForum: Boolean,
        forceHubReconcile: Boolean,
        forceNewsReconcile: Boolean,
        forceForumReconcile: Boolean,
        includeReactionLog: Boolean,
    ) {
        pending = (pending ?: OverlayInboxCoalescedRequest()).mergeWith(
            includeHub = includeHub,
            includeNews = includeNews,
            includeForum = includeForum,
            forceHubReconcile = forceHubReconcile,
            forceNewsReconcile = forceNewsReconcile,
            forceForumReconcile = forceForumReconcile,
            includeReactionLog = includeReactionLog,
        )
    }

    fun takePending(): OverlayInboxCoalescedRequest? {
        val next = pending
        pending = null
        return next
    }

    fun hasPending(): Boolean = pending != null
}
