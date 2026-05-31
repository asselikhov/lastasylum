package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster

internal data class OverlayReactionLogDisplayRow(
    val headerKey: String?,
    val cluster: OverlayReactionLogCluster?,
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
