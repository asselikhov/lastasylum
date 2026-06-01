package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogClusterPolicy
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogFeedBuilder
import com.lastasylum.alliance.data.chat.OverlayReactionLogFeedItem
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy

object OverlayReactionNotificationsDeriver {
    fun filterKey(
        directionFilter: OverlayReactionLogFilter,
        scopeFilter: OverlayReactionLogScopeFilter,
        debouncedSearch: String,
    ): String = buildString {
        append(directionFilter.name)
        append('|')
        append(scopeFilter.name)
        append('|')
        append(debouncedSearch.trim())
    }

    fun filterEntries(
        entries: List<OverlayReactionLogEntry>,
        selfUserId: String,
        directionFilter: OverlayReactionLogFilter,
        scopeFilter: OverlayReactionLogScopeFilter,
        searchQuery: String,
    ): List<OverlayReactionLogEntry> =
        entries
            .filter { OverlayReactionLogVisibilityPolicy.matchesFilter(it, selfUserId, directionFilter) }
            .filter { OverlayReactionLogVisibilityPolicy.matchesScopeFilter(it, scopeFilter) }
            .filter { OverlayReactionLogVisibilityPolicy.matchesSearchQuery(it, searchQuery) }

    fun clusterFiltered(
        filtered: List<OverlayReactionLogEntry>,
        selfUserId: String,
    ): List<OverlayReactionLogCluster> =
        OverlayReactionLogClusterPolicy.clusterEntries(filtered, selfUserId)

    fun buildGroupedFeed(
        entries: List<OverlayReactionLogEntry>,
        selfUserId: String,
        directionFilter: OverlayReactionLogFilter,
        scopeFilter: OverlayReactionLogScopeFilter,
        searchQuery: String,
    ): Pair<List<OverlayReactionLogCluster>, List<Pair<String, List<OverlayReactionLogFeedItem>>>> {
        val filtered = filterEntries(entries, selfUserId, directionFilter, scopeFilter, searchQuery)
        val feedItems = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = filtered,
            allEntries = entries,
            selfUserId = selfUserId,
            directionFilter = directionFilter,
        )
        val clustered = feedItems.flatMap { item ->
            when (item) {
                is OverlayReactionLogFeedItem.Root -> listOf(item.cluster)
                is OverlayReactionLogFeedItem.ThreadParent ->
                    listOf(item.parent) + item.replies
            }
        }
        return clustered to OverlayReactionLogFeedBuilder.groupFeedItems(feedItems)
    }

    fun resolveOnlineUserIds(
        clustered: List<OverlayReactionLogCluster>,
    ): Set<String> {
        val ids = clustered
            .flatMap { it.entries }
            .map { it.senderUserId.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return ids.filterTo(mutableSetOf()) { OverlayMemberPresenceLookup.isInGameNow(it) }
    }
}
