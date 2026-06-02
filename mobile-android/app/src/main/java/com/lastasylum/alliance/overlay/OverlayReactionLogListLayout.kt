package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogFeedItem

internal data class OverlayReactionLogDisplayRow(
    val headerKey: String?,
    val cluster: OverlayReactionLogCluster?,
)

data class OverlayReactionLogStickyListLayout(
    val groupedFeed: List<Pair<String, List<OverlayReactionLogFeedItem>>>,
    val firstUnreadItemIndex: Int,
    /** Lazy index of the newest cluster (top of feed when newest-first). */
    val latestClusterItemIndex: Int,
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

fun feedItemPrimaryEntryId(item: OverlayReactionLogFeedItem): String =
    when (item) {
        is OverlayReactionLogFeedItem.Root -> item.cluster.representative.id
        is OverlayReactionLogFeedItem.ThreadParent -> item.parent.representative.id
    }

/** Newest-first entry ids for preview animation (first rows in newest-first feed). */
fun buildNewestFeedEntryIds(
    groupedFeed: List<Pair<String, List<OverlayReactionLogFeedItem>>>,
    limit: Int = OverlayReactionPreviewAnimationPolicy.MAX_CONCURRENT_ANIMATED_PREVIEWS,
): List<String> {
    val ids = mutableListOf<String>()
    groupedFeed.forEach { (_, feedItems) ->
        feedItems.forEach { item ->
            if (ids.size >= limit) return ids
            ids += feedItemPrimaryEntryId(item)
        }
    }
    return ids
}

fun parseOverlayReactionLogListKey(key: Any?): String? = when (key) {
    is String -> when {
        key.startsWith(CLUSTER_KEY_PREFIX) -> key.removePrefix(CLUSTER_KEY_PREFIX)
        key.startsWith(THREAD_KEY_PREFIX) -> key.removePrefix(THREAD_KEY_PREFIX)
        else -> null
    }
    else -> null
}

fun findThreadParentFeedItem(
    groupedFeed: List<Pair<String, List<OverlayReactionLogFeedItem>>>,
    parentLogId: String,
): OverlayReactionLogFeedItem.ThreadParent? {
    groupedFeed.forEach { (_, feedItems) ->
        feedItems.forEach { item ->
            if (item is OverlayReactionLogFeedItem.ThreadParent &&
                item.parent.representative.id == parentLogId
            ) {
                return item
            }
        }
    }
    return null
}

fun collectMarkReadIdsForListKey(
    listKey: String,
    groupedFeed: List<Pair<String, List<OverlayReactionLogFeedItem>>>,
    unreadEntryIds: Set<String>,
): Set<String> {
    if (unreadEntryIds.isEmpty()) return emptySet()
    return when {
        listKey.startsWith(CLUSTER_KEY_PREFIX) -> {
            val id = listKey.removePrefix(CLUSTER_KEY_PREFIX)
            if (id in unreadEntryIds) setOf(id) else emptySet()
        }
        listKey.startsWith(THREAD_KEY_PREFIX) -> {
            val parentId = listKey.removePrefix(THREAD_KEY_PREFIX)
            buildSet {
                if (parentId in unreadEntryIds) add(parentId)
                findThreadParentFeedItem(groupedFeed, parentId)?.replies?.forEach { reply ->
                    val replyId = reply.representative.id
                    if (replyId in unreadEntryIds) add(replyId)
                }
            }
        }
        else -> emptySet()
    }
}

private const val CLUSTER_KEY_PREFIX = "cluster-"
private const val THREAD_KEY_PREFIX = "thread-"

internal fun buildStickyListLayout(
    groupedFeed: List<Pair<String, List<OverlayReactionLogFeedItem>>>,
    loadingMore: Boolean,
    unreadEntryIds: Set<String>,
): OverlayReactionLogStickyListLayout {
    var index = 0
    var firstUnread = -1
    var latestCluster = -1
    val itemIndexToEntryId = mutableMapOf<Int, String>()
    groupedFeed.forEach { (_, feedItems) ->
        index++
        feedItems.forEach { feedItem ->
            val entryId = feedItemPrimaryEntryId(feedItem)
            if (entryId in unreadEntryIds && firstUnread < 0) {
                firstUnread = index
            }
            if (latestCluster < 0) {
                latestCluster = index
            }
            itemIndexToEntryId[index] = entryId
            index++
        }
    }
    if (loadingMore) {
        index++
    }
    return OverlayReactionLogStickyListLayout(
        groupedFeed = groupedFeed,
        firstUnreadItemIndex = firstUnread,
        latestClusterItemIndex = latestCluster,
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
