package com.lastasylum.alliance.ui.components.team

/** Animation budget for feed card ambient effects (fire, shimmer). */
enum class FeedAnimationTier {
    /** Full canvas fire / multi-transition ambient. */
    Full,
    /** Border shimmer + static gradient only. */
    Lite,
    /** No animated canvas; accent rail only. */
    Off,
}

/**
 * Resolves animation tier for a forum topic card.
 * @param visibleUnreadRank rank among visible unread items (0 = top); -1 if not unread/visible.
 */
fun forumTopicAnimationTier(
    unread: Boolean,
    isVisible: Boolean,
    visibleUnreadRank: Int,
    listSize: Int,
    sectionActive: Boolean,
    overlayMode: Boolean,
    warmActivity: Boolean = false,
): FeedAnimationTier {
    if (!sectionActive || !isVisible) return FeedAnimationTier.Off
    if (unread) {
        if (overlayMode && listSize > 20) return FeedAnimationTier.Off
        if (overlayMode && visibleUnreadRank > 1) {
            return if (visibleUnreadRank <= 5) FeedAnimationTier.Lite else FeedAnimationTier.Off
        }
        return when {
            visibleUnreadRank <= 2 -> FeedAnimationTier.Full
            visibleUnreadRank <= 5 -> FeedAnimationTier.Lite
            else -> FeedAnimationTier.Off
        }
    }
    if (warmActivity) return FeedAnimationTier.Lite
    return FeedAnimationTier.Off
}

/** Cyan wave tier for unread news cards in journal feed. */
fun journalUnreadAnimationTier(
    isUnread: Boolean,
    isVisible: Boolean,
    visibleUnreadRank: Int,
    sectionActive: Boolean,
): FeedAnimationTier {
    if (!sectionActive || !isUnread || !isVisible) return FeedAnimationTier.Off
    return when {
        visibleUnreadRank <= 1 -> FeedAnimationTier.Full
        visibleUnreadRank <= 3 -> FeedAnimationTier.Lite
        else -> FeedAnimationTier.Off
    }
}

/**
 * Builds visible-unread rank map: topicId -> rank (0 = first visible unread in list order).
 */
fun buildVisibleUnreadRankMap(
    visibleIndices: List<Int>,
    unreadAtIndex: (Int) -> Boolean,
): Map<Int, Int> {
    var rank = 0
    val result = mutableMapOf<Int, Int>()
    visibleIndices.forEach { index ->
        if (unreadAtIndex(index)) {
            result[index] = rank
            rank++
        }
    }
    return result
}
