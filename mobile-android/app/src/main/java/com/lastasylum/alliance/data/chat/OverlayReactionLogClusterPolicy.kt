package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.ui.util.parseIsoInstant
import java.time.Instant

data class OverlayReactionLogCluster(
    val entries: List<OverlayReactionLogEntry>,
) {
    val representative: OverlayReactionLogEntry
        get() = entries.first()

    val mergeCount: Int
        get() = entries.size
}

object OverlayReactionLogClusterPolicy {
    const val MERGE_WINDOW_MS = 2_000L

    fun clusterEntries(
        entries: List<OverlayReactionLogEntry>,
        selfUserId: String,
    ): List<OverlayReactionLogCluster> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries.sortedByDescending { it.id }
        val clusters = mutableListOf<OverlayReactionLogCluster>()
        var bucket = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val candidate = sorted[i]
            val head = bucket.first()
            if (canMerge(head, candidate, selfUserId)) {
                bucket.add(candidate)
            } else {
                clusters.add(OverlayReactionLogCluster(bucket.toList()))
                bucket = mutableListOf(candidate)
            }
        }
        clusters.add(OverlayReactionLogCluster(bucket.toList()))
        return clusters
    }

    private fun isReplyEntry(entry: OverlayReactionLogEntry): Boolean =
        entry.replyToLog != null

    fun canMerge(
        newer: OverlayReactionLogEntry,
        older: OverlayReactionLogEntry,
        selfUserId: String,
    ): Boolean {
        if (isReplyEntry(newer) || isReplyEntry(older)) return false
        if (newer.senderUserId.trim() != older.senderUserId.trim()) return false
        if (newer.visibility != older.visibility) return false
        val newerIncoming = OverlayReactionLogVisibilityPolicy.isIncoming(newer, selfUserId)
        val olderIncoming = OverlayReactionLogVisibilityPolicy.isIncoming(older, selfUserId)
        if (newerIncoming != olderIncoming) return false
        val newerMs = parseEntryInstantMs(newer.createdAt) ?: return false
        val olderMs = parseEntryInstantMs(older.createdAt) ?: return false
        return newerMs - olderMs <= MERGE_WINDOW_MS
    }

    private fun parseEntryInstantMs(createdAt: String): Long? =
        parseIsoInstant(createdAt.trim())?.toEpochMilli()
}
