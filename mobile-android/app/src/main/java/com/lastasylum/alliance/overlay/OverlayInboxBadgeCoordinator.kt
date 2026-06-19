package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.InboxUnreadReconciler
import com.lastasylum.alliance.data.displayedUnreadCount
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.ForumUnreadCounts
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.data.teams.TeamInboxBadgeDeriver
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Overlay HUD badges for team news/forum — independent caches, optimistic floors, and refresh.
 */
class OverlayInboxBadgeCoordinator {
    @Volatile
    private var cachedNewsUnread: Int = 0

    @Volatile
    private var cachedForumUnread: Int = 0

    @Volatile
    private var cachedForumRawUnread: Int = 0

    @Volatile
    private var cachedBadgeTeamId: String? = null

    @Volatile
    private var cachedBadgeAtMs: Long = 0L

    @Volatile
    var newsOptimisticFloor: Int = 0
        private set

    @Volatile
    var forumOptimisticFloor: Int = 0
        private set

    var newsLastBumpAtMs: Long = 0L
        private set

    var forumLastBumpAtMs: Long = 0L
        private set

    var forumOptimisticUntilMs: Long = 0L
        private set

    @Volatile
    var newsOptimisticUntilMs: Long = 0L
        private set

    fun invalidateNewsCache() {
        cachedBadgeAtMs = 0L
    }

    fun invalidateForumCache() {
        cachedBadgeAtMs = 0L
    }

    /** Invalidate overlay coordinator + [OverlayGameStatusHudRefresh] news caches together. */
    fun invalidateNewsBadgeCachesFully() {
        invalidateNewsCache()
        OverlayGameStatusHudRefresh.invalidateNewsCache()
    }

    /** Invalidate overlay coordinator + [OverlayGameStatusHudRefresh] forum caches together. */
    fun invalidateForumBadgeCachesFully() {
        invalidateForumCache()
        OverlayGameStatusHudRefresh.invalidateForumCache()
    }

    fun invalidateAllCaches() {
        cachedBadgeAtMs = 0L
    }

    fun bumpForumOptimistic(displayed: Int) {
        forumLastBumpAtMs = System.currentTimeMillis()
        forumOptimisticUntilMs = System.currentTimeMillis() + FORUM_OPTIMISTIC_BADGE_MS
        forumOptimisticFloor = displayed.coerceIn(0, MAX_BADGE)
    }

    fun bumpNewsOptimistic(displayed: Int) {
        newsLastBumpAtMs = System.currentTimeMillis()
        newsOptimisticUntilMs = System.currentTimeMillis() + NEWS_OPTIMISTIC_BADGE_MS
        newsOptimisticFloor = displayed.coerceIn(0, MAX_BADGE)
    }

    fun clearNewsOptimistic() {
        newsOptimisticFloor = 0
        newsOptimisticUntilMs = 0L
    }

    fun clearForumOptimistic() {
        forumOptimisticFloor = 0
        forumOptimisticUntilMs = 0L
    }

    fun shouldDeferNewsReconcile(): Boolean {
        if (newsOptimisticFloor <= 0) return false
        return System.currentTimeMillis() - newsLastBumpAtMs < RECONCILE_GRACE_MS
    }

    fun shouldDeferForumReconcile(): Boolean {
        if (forumOptimisticFloor <= 0) return false
        return System.currentTimeMillis() - forumLastBumpAtMs < RECONCILE_GRACE_MS
    }

    fun isForumOptimisticActive(): Boolean =
        System.currentTimeMillis() < forumOptimisticUntilMs

    fun isNewsOptimisticActive(): Boolean =
        System.currentTimeMillis() < newsOptimisticUntilMs

    suspend fun fetchNewsUnread(
        context: android.content.Context,
        teamId: String,
        currentUserId: String,
    ): Int = withContext(Dispatchers.IO) {
        val container = AppContainer.from(context)
        val prefs = container.userSettingsPreferences
        val newsAfter = prefs.getLastSeenTeamNewsCreatedAt(teamId)
        val badges = container.teamsRepository.getTeamInboxBadges(teamId, newsAfter).getOrNull()
        val fromApi = badges?.newsUnread?.coerceAtLeast(0)
        val clientUnread = container.teamsRepository.listTeamNews(teamId, cursor = null, limit = 40)
            .getOrNull()
            ?.items
            ?.let { TeamInboxUnread.countUnreadNews(it, prefs, teamId, currentUserId) }
        return@withContext TeamInboxBadgeDeriver.resolveNewsUnread(
            clientUnread = clientUnread,
            apiUnread = fromApi,
        )
    }

    suspend fun fetchForumUnread(
        context: android.content.Context,
        teamId: String,
    ): Int = fetchForumUnreadCounts(context, teamId).effective

    suspend fun fetchForumUnreadCounts(
        context: android.content.Context,
        teamId: String,
    ): ForumUnreadCounts = withContext(Dispatchers.IO) {
        val container = AppContainer.from(context)
        val prefs = container.userSettingsPreferences
        val newsAfter = prefs.getLastSeenTeamNewsCreatedAt(teamId)
        val apiForumUnread = container.teamsRepository
            .getTeamInboxBadges(teamId, newsAfter)
            .getOrNull()
            ?.forumUnread
            ?.coerceAtLeast(0)
        val clientCounts = TeamInboxBadgeDeriver.computeForumUnreadCountsFromRepository(
            teamsRepository = container.teamsRepository,
            forumPrefs = container.teamForumPreferences,
            teamId = teamId,
        )
        val effective = TeamInboxBadgeDeriver.resolveForumUnread(
            clientUnread = clientCounts.effective,
            apiUnread = apiForumUnread,
        )
        val rawServer = maxOf(clientCounts.rawServer, apiForumUnread ?: 0)
        ForumUnreadCounts(effective = effective, rawServer = rawServer)
    }

    fun mergeNewsDisplayed(
        serverCount: Int,
        previouslyDisplayed: Int,
    ): Int = displayedUnreadCount(
        effectiveUnread = serverCount.coerceAtLeast(0),
        previouslyDisplayed = previouslyDisplayed,
        rawServerUnread = serverCount.coerceAtLeast(0),
        optimisticFloor = newsOptimisticFloor,
    ).also { merged ->
        if (serverCount > 0 && merged >= serverCount) {
            newsOptimisticFloor = 0
        } else if (merged <= 0) {
            newsOptimisticFloor = 0
        }
    }

    fun mergeForumDisplayed(
        serverCount: Int,
        previouslyDisplayed: Int,
        rawServerCount: Int = serverCount,
    ): Int {
        val raw = rawServerCount.coerceAtLeast(0)
        val effective = serverCount.coerceAtLeast(0)
        return displayedUnreadCount(
            effectiveUnread = effective,
            previouslyDisplayed = previouslyDisplayed,
            rawServerUnread = raw,
            optimisticFloor = forumOptimisticFloor,
        ).also { merged ->
            if (effective > 0 && merged >= effective) {
                forumOptimisticFloor = 0
            } else if (merged <= 0 && !isForumOptimisticActive()) {
                forumOptimisticFloor = 0
            }
        }
    }

    fun onForumMarkedReadLocally() {
        clearForumOptimistic()
        invalidateForumCache()
    }

    fun mergeHudNews(
        authoritative: Int,
        prevDisplayed: Int,
        useAuthoritative: Boolean,
    ): Int {
        if (authoritative <= 0 && !shouldDeferNewsReconcile() && !isNewsOptimisticActive()) {
            clearNewsOptimistic()
        }
        return when {
            isNewsOptimisticActive() || shouldDeferNewsReconcile() ->
                maxOf(authoritative, prevDisplayed, newsOptimisticFloor)
            useAuthoritative -> mergeNewsDisplayed(authoritative, prevDisplayed)
            else -> mergeNewsDisplayed(authoritative, prevDisplayed)
        }
    }

    fun mergeHudForum(
        authoritative: Int,
        prevDisplayed: Int,
        useAuthoritative: Boolean,
        rawAuthoritative: Int = authoritative,
    ): Int {
        if (authoritative <= 0 && rawAuthoritative <= 0 &&
            !shouldDeferForumReconcile() && !isForumOptimisticActive()
        ) {
            clearForumOptimistic()
        }
        return when {
            isForumOptimisticActive() || shouldDeferForumReconcile() ->
                maxOf(maxOf(authoritative, rawAuthoritative), prevDisplayed, forumOptimisticFloor)
            useAuthoritative -> mergeForumDisplayed(authoritative, prevDisplayed, rawAuthoritative)
            else -> mergeForumDisplayed(authoritative, prevDisplayed, rawAuthoritative)
        }
    }

    /** Cache server-authoritative counts only — never merged/displayed badge values. */
    fun cacheAuthoritativeNews(teamId: String, serverCount: Int) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        cachedBadgeTeamId = tid
        cachedNewsUnread = serverCount.coerceAtLeast(0)
        cachedBadgeAtMs = System.currentTimeMillis()
    }

    fun cacheAuthoritativeForum(teamId: String, effective: Int, rawServer: Int) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        cachedBadgeTeamId = tid
        cachedForumUnread = effective.coerceAtLeast(0)
        cachedForumRawUnread = rawServer.coerceAtLeast(0)
        cachedBadgeAtMs = System.currentTimeMillis()
    }

    fun cacheAuthoritativeNewsForum(
        teamId: String,
        newsServer: Int,
        forumEffective: Int,
        forumRawServer: Int = forumEffective,
    ) {
        cacheAuthoritativeNews(teamId, newsServer)
        cacheAuthoritativeForum(teamId, forumEffective, forumRawServer)
    }

    fun readCachedNews(teamId: String): Int? {
        if (!isCacheFresh(teamId)) return null
        return cachedNewsUnread
    }

    fun readCachedForum(teamId: String): Int? {
        if (!isCacheFresh(teamId)) return null
        return cachedForumUnread
    }

    fun readCachedForumCounts(teamId: String): ForumUnreadCounts? {
        if (!isCacheFresh(teamId)) return null
        return ForumUnreadCounts(
            effective = cachedForumUnread,
            rawServer = cachedForumRawUnread,
        )
    }

    private fun isCacheFresh(teamId: String): Boolean {
        if (teamId.isEmpty() || teamId != cachedBadgeTeamId) return false
        return System.currentTimeMillis() - cachedBadgeAtMs < CACHE_TTL_MS
    }

    companion object {
        private const val MAX_BADGE = 999
        const val RECONCILE_GRACE_MS = OverlayHubUnreadPolicy.RECONCILE_GRACE_MS
        const val FORUM_OPTIMISTIC_BADGE_MS = 8_000L
        const val NEWS_OPTIMISTIC_BADGE_MS = 8_000L
        private const val CACHE_TTL_MS = 180_000L
    }
}
