package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogClusterPolicy
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
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

    fun groupClusters(
        clustered: List<OverlayReactionLogCluster>,
    ): List<Pair<String, List<OverlayReactionLogCluster>>> =
        clustered
            .groupBy { overlayReactionLogDateHeaderKey(it.representative.createdAt) }
            .toList()
            .sortedBy { (_, group) -> group.firstOrNull()?.representative?.id.orEmpty() }
            .map { (headerKey, groupClusters) ->
                headerKey to groupClusters.sortedBy { it.representative.id }
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
