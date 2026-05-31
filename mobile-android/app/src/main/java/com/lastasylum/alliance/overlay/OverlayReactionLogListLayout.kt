package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster

internal data class OverlayReactionLogDisplayRow(
    val headerKey: String?,
    val cluster: OverlayReactionLogCluster?,
)

internal data class OverlayReactionLogStickyListLayout(
    val grouped: List<Pair<String, List<OverlayReactionLogCluster>>>,
    val firstUnreadItemIndex: Int,
    val lastClusterItemIndex: Int,
    val itemIndexToEntryId: Map<Int, String>,
)

internal fun buildOverlayReactionLogDisplayRows(
    grouped: List<Pair<String, List<OverlayReactionLogCluster>>>,
    loadingMore: Boolean,
): List<OverlayReactionLogDisplayRow> {
    val rows = mutableListOf<OverlayReactionLogDisplayRow>()
    if (loadingMore) {
        rows += OverlayReactionLogDisplayRow(headerKey = null, cluster = null)
    }
    grouped.forEach { (headerKey, clusters) ->
        rows += OverlayReactionLogDisplayRow(headerKey = headerKey, cluster = null)
        clusters.forEach { cluster ->
            rows += OverlayReactionLogDisplayRow(headerKey = null, cluster = cluster)
        }
    }
    return rows
}

internal fun buildStickyListLayout(
    grouped: List<Pair<String, List<OverlayReactionLogCluster>>>,
    loadingMore: Boolean,
    unreadEntryIds: Set<String>,
): OverlayReactionLogStickyListLayout {
    var index = 0
    var firstUnread = -1
    var lastCluster = -1
    val itemIndexToEntryId = mutableMapOf<Int, String>()
    if (loadingMore) {
        index++
    }
    grouped.forEach { (_, clusters) ->
        index++
        clusters.forEach { cluster ->
            val entryId = cluster.representative.id
            if (entryId in unreadEntryIds && firstUnread < 0) {
                firstUnread = index
            }
            lastCluster = index
            itemIndexToEntryId[index] = entryId
            index++
        }
    }
    return OverlayReactionLogStickyListLayout(
        grouped = grouped,
        firstUnreadItemIndex = firstUnread,
        lastClusterItemIndex = lastCluster,
        itemIndexToEntryId = itemIndexToEntryId,
    )
}

internal fun firstUnreadDisplayIndex(
    rows: List<OverlayReactionLogDisplayRow>,
    isUnread: (OverlayReactionLogCluster) -> Boolean,
): Int {
    rows.forEachIndexed { index, row ->
        val cluster = row.cluster ?: return@forEachIndexed
        if (isUnread(cluster)) return index
    }
    return -1
}

internal fun lastClusterDisplayIndex(rows: List<OverlayReactionLogDisplayRow>): Int {
    for (index in rows.lastIndex downTo 0) {
        if (rows[index].cluster != null) return index
    }
    return -1
}
