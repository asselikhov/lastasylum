package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.overlay.overlayReactionLogDateHeaderKey

sealed class OverlayReactionLogFeedItem {
    data class Root(val cluster: OverlayReactionLogCluster) : OverlayReactionLogFeedItem()

    data class ThreadParent(
        val parent: OverlayReactionLogCluster,
        val replies: List<OverlayReactionLogCluster>,
    ) : OverlayReactionLogFeedItem()
}

object OverlayReactionLogFeedBuilder {
    fun buildFeedItems(
        filteredEntries: List<OverlayReactionLogEntry>,
        allEntries: List<OverlayReactionLogEntry>,
        selfUserId: String,
        directionFilter: OverlayReactionLogFilter,
    ): List<OverlayReactionLogFeedItem> {
        val enrichedAll = OverlayReactionLogReplyEnricher.enrichEntries(allEntries)
        val filteredIdSet = filteredEntries.map { it.id }.toSet()

        val clustered = OverlayReactionLogClusterPolicy.clusterEntries(filteredEntries, selfUserId)
        val repliesByParent = repliesGroupedByParent(enrichedAll)
        val anchoredParentIds = anchoredParentIdsForReplies(
            repliesByParent = repliesByParent,
            enrichedAll = enrichedAll,
            filteredIdSet = filteredIdSet,
            selfUserId = selfUserId,
            directionFilter = directionFilter,
        )
        val replyClustersByParent = replyClustersForParents(
            anchoredParentIds = anchoredParentIds,
            repliesByParent = repliesByParent,
            filteredIdSet = filteredIdSet,
        )

        val items = mutableListOf<OverlayReactionLogFeedItem>()
        val emittedParentIds = mutableSetOf<String>()

        clustered.forEach { cluster ->
            val rep = cluster.representative
            when {
                rep.id in anchoredParentIds -> {
                    emittedParentIds.add(rep.id)
                    items.add(
                        OverlayReactionLogFeedItem.ThreadParent(
                            parent = cluster,
                            replies = replyClustersByParent[rep.id].orEmpty(),
                        ),
                    )
                }
                parentLogIdInAnchored(rep, anchoredParentIds) -> Unit
                else -> items.add(OverlayReactionLogFeedItem.Root(cluster))
            }
        }

        anchoredParentIds
            .filter { it !in emittedParentIds }
            .sortedByDescending { it }
            .forEach { parentId ->
                val parentEntry = enrichedAll.find { it.id == parentId } ?: return@forEach
                items.add(
                    OverlayReactionLogFeedItem.ThreadParent(
                        parent = OverlayReactionLogCluster(listOf(parentEntry)),
                        replies = replyClustersByParent[parentId].orEmpty(),
                    ),
                )
            }

        return items.sortedByDescending { feedItemSortKey(it) }
    }

    private fun repliesGroupedByParent(
        entries: List<OverlayReactionLogEntry>,
    ): Map<String, List<OverlayReactionLogEntry>> =
        entries
            .mapNotNull { entry ->
                val parentId = OverlayReactionLogReplyEnricher.parentLogId(entry)
                    ?: return@mapNotNull null
                parentId to entry
            }
            .groupBy({ it.first }, { it.second })

    private fun anchoredParentIdsForReplies(
        repliesByParent: Map<String, List<OverlayReactionLogEntry>>,
        enrichedAll: List<OverlayReactionLogEntry>,
        filteredIdSet: Set<String>,
        selfUserId: String,
        directionFilter: OverlayReactionLogFilter,
    ): Set<String> =
        repliesByParent.keys.filterTo(mutableSetOf()) { parentId ->
            val parentEntry = enrichedAll.find { it.id == parentId } ?: return@filterTo false
            val hasVisibleReplies = repliesByParent[parentId]?.any { it.id in filteredIdSet } == true
            hasVisibleReplies &&
                parentMatchesDirectionFilter(parentEntry, selfUserId, directionFilter)
        }

    private fun parentMatchesDirectionFilter(
        parentEntry: OverlayReactionLogEntry,
        selfUserId: String,
        directionFilter: OverlayReactionLogFilter,
    ): Boolean = when (directionFilter) {
        OverlayReactionLogFilter.All -> true
        OverlayReactionLogFilter.Incoming ->
            OverlayReactionLogVisibilityPolicy.isIncoming(parentEntry, selfUserId)
        OverlayReactionLogFilter.Outgoing ->
            OverlayReactionLogVisibilityPolicy.isOutgoing(parentEntry, selfUserId)
    }

    private fun replyClustersForParents(
        anchoredParentIds: Set<String>,
        repliesByParent: Map<String, List<OverlayReactionLogEntry>>,
        filteredIdSet: Set<String>,
    ): Map<String, List<OverlayReactionLogCluster>> =
        anchoredParentIds.associateWith { parentId ->
            (repliesByParent[parentId] ?: emptyList())
                .filter { it.id in filteredIdSet }
                .sortedByDescending { it.id }
                .map { OverlayReactionLogCluster(listOf(it)) }
        }

    private fun parentLogIdInAnchored(
        entry: OverlayReactionLogEntry,
        anchoredParentIds: Set<String>,
    ): Boolean {
        val parentId = OverlayReactionLogReplyEnricher.parentLogId(entry)?.trim().orEmpty()
        return parentId.isNotEmpty() && parentId in anchoredParentIds
    }

    private fun feedItemSortKey(item: OverlayReactionLogFeedItem): String =
        when (item) {
            is OverlayReactionLogFeedItem.Root -> item.cluster.representative.id
            is OverlayReactionLogFeedItem.ThreadParent -> item.parent.representative.id
        }

    fun groupFeedItems(
        feedItems: List<OverlayReactionLogFeedItem>,
    ): List<Pair<String, List<OverlayReactionLogFeedItem>>> =
        feedItems
            .groupBy { feedItem ->
                val createdAt = when (feedItem) {
                    is OverlayReactionLogFeedItem.Root ->
                        feedItem.cluster.representative.createdAt
                    is OverlayReactionLogFeedItem.ThreadParent ->
                        feedItem.parent.representative.createdAt
                }
                overlayReactionLogDateHeaderKey(createdAt)
            }
            .toList()
            .sortedByDescending { (_, group) ->
                group.firstOrNull()?.let { feedItemSortKey(it) } ?: ""
            }
            .map { (headerKey, groupItems) ->
                headerKey to groupItems.sortedByDescending { feedItemSortKey(it) }
            }
}
