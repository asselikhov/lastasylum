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
        val clustered = OverlayReactionLogClusterPolicy.clusterEntries(filteredEntries, selfUserId)

        if (directionFilter == OverlayReactionLogFilter.Reply) {
            return clustered
                .filter { it.representative.replyToLog != null }
                .map { OverlayReactionLogFeedItem.Root(it) }
        }

        val repliesByParent = allEntries
            .mapNotNull { entry ->
                val parentId = entry.replyToLog?.logId?.trim().orEmpty()
                if (parentId.isEmpty()) null else parentId to entry
            }
            .groupBy({ it.first }, { it.second })

        val filteredParentIds = filteredEntries.map { it.id }.toSet()
        val anchoredParentIds = repliesByParent.keys.filterTo(mutableSetOf()) { parentId ->
            val parentEntry = allEntries.find { it.id == parentId } ?: return@filterTo false
            parentId in filteredParentIds &&
                OverlayReactionLogVisibilityPolicy.isOutgoing(parentEntry, selfUserId) &&
                !repliesByParent[parentId].isNullOrEmpty()
        }

        val replyClustersByParent = anchoredParentIds.associateWith { parentId ->
            (repliesByParent[parentId] ?: emptyList())
                .sortedByDescending { it.id }
                .map { OverlayReactionLogCluster(listOf(it)) }
        }

        return clustered.mapNotNull { cluster ->
            val rep = cluster.representative
            when {
                rep.id in anchoredParentIds -> {
                    OverlayReactionLogFeedItem.ThreadParent(
                        parent = cluster,
                        replies = replyClustersByParent[rep.id].orEmpty(),
                    )
                }
                rep.replyToLog?.logId?.trim().orEmpty() in anchoredParentIds -> null
                else -> OverlayReactionLogFeedItem.Root(cluster)
            }
        }
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
            .sortedBy { (_, group) ->
                group.firstOrNull().let { item ->
                    when (item) {
                        is OverlayReactionLogFeedItem.Root -> item.cluster.representative.id
                        is OverlayReactionLogFeedItem.ThreadParent -> item.parent.representative.id
                        null -> ""
                    }
                }
            }
            .map { (headerKey, groupItems) ->
                headerKey to groupItems.sortedBy { item ->
                    when (item) {
                        is OverlayReactionLogFeedItem.Root -> item.cluster.representative.id
                        is OverlayReactionLogFeedItem.ThreadParent -> item.parent.representative.id
                    }
                }
            }
}
