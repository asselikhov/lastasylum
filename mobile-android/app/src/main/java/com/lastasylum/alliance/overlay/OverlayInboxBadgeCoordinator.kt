package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.InboxUnreadReconciler
import com.lastasylum.alliance.data.displayedUnreadCount
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.data.teams.TeamInboxBadgeDeriver
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Overlay HUD badges for team news/forum — independent caches, optimistic floors, and refresh.
 */
internal class OverlayInboxBadgeCoordinator {
    @Volatile
    private var cachedNewsUnread: Int = 0

    @Volatile
    private var cachedForumUnread: Int = 0

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

    fun invalidateNewsCache() {
        cachedBadgeAtMs = 0L
    }

    fun invalidateForumCache() {
        cachedBadgeAtMs = 0L
    }

    fun invalidateAllCaches() {
        cachedBadgeAtMs = 0L
    }

    fun bumpForumOptimistic(displayed: Int) {
        forumLastBumpAtMs = System.currentTimeMillis()
        forumOptimisticUntilMs = System.currentTimeMillis() + FORUM_OPTIMISTIC_BADGE_MS
        forumOptimisticFloor = displayed.coerceIn(0, MAX_BADGE)
    }

    fun clearNewsOptimistic() {
        newsOptimisticFloor = 0
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

    suspend fun fetchNewsUnread(
        context: android.content.Context,
        teamId: String,
        currentUserId: String,
    ): Int = withContext(Dispatchers.IO) {
        val container = AppContainer.from(context)
        val prefs = container.userSettingsPreferences
        val newsAfter = prefs.getLastSeenTeamNewsCreatedAt()
        val badges = container.teamsRepository.getTeamInboxBadges(teamId, newsAfter).getOrNull()
        val fromApi = badges?.newsUnread?.coerceAtLeast(0)
        if (fromApi != null) return@withContext fromApi
        container.teamsRepository.listTeamNews(teamId, cursor = null, limit = 40)
            .getOrNull()
            ?.items
            ?.let { TeamInboxUnread.countUnreadNews(it, prefs, currentUserId) }
            ?: 0
    }

    suspend fun fetchForumUnread(
        context: android.content.Context,
        teamId: String,
    ): Int = withContext(Dispatchers.IO) {
        val container = AppContainer.from(context)
        TeamInboxBadgeDeriver.computeForumUnreadFromRepository(
            teamsRepository = container.teamsRepository,
            forumPrefs = container.teamForumPreferences,
            teamId = teamId,
        )
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
    ): Int = displayedUnreadCount(
        effectiveUnread = serverCount.coerceAtLeast(0),
        previouslyDisplayed = previouslyDisplayed,
        rawServerUnread = serverCount.coerceAtLeast(0),
        optimisticFloor = forumOptimisticFloor,
    ).also { merged ->
        if (serverCount > 0 && merged >= serverCount) {
            forumOptimisticFloor = 0
        } else if (merged <= 0 && !isForumOptimisticActive()) {
            forumOptimisticFloor = 0
        }
    }

    fun mergeHudNews(
        authoritative: Int,
        prevDisplayed: Int,
        useAuthoritative: Boolean,
    ): Int {
        if (authoritative <= 0 && !shouldDeferNewsReconcile()) {
            clearNewsOptimistic()
        }
        return when {
            shouldDeferNewsReconcile() ->
                maxOf(authoritative, prevDisplayed, newsOptimisticFloor)
            useAuthoritative -> mergeNewsDisplayed(authoritative, prevDisplayed)
            else -> mergeNewsDisplayed(authoritative, prevDisplayed)
        }
    }

    fun mergeHudForum(
        authoritative: Int,
        prevDisplayed: Int,
        useAuthoritative: Boolean,
    ): Int {
        if (authoritative <= 0 && !shouldDeferForumReconcile() && !isForumOptimisticActive()) {
            clearForumOptimistic()
        }
        return when {
            isForumOptimisticActive() || shouldDeferForumReconcile() ->
                maxOf(authoritative, prevDisplayed, forumOptimisticFloor)
            useAuthoritative -> mergeForumDisplayed(authoritative, prevDisplayed)
            else -> mergeForumDisplayed(authoritative, prevDisplayed)
        }
    }

    fun cacheNewsForum(teamId: String, news: Int, forum: Int) {
        cachedBadgeTeamId = teamId
        cachedNewsUnread = news
        cachedForumUnread = forum
        cachedBadgeAtMs = System.currentTimeMillis()
    }

    fun readCachedNews(teamId: String): Int? {
        if (!isCacheFresh(teamId)) return null
        return cachedNewsUnread
    }

    fun readCachedForum(teamId: String): Int? {
        if (!isCacheFresh(teamId)) return null
        return cachedForumUnread
    }

    private fun isCacheFresh(teamId: String): Boolean {
        if (teamId.isEmpty() || teamId != cachedBadgeTeamId) return false
        return System.currentTimeMillis() - cachedBadgeAtMs < CACHE_TTL_MS
    }

    companion object {
        private const val MAX_BADGE = 999
        const val RECONCILE_GRACE_MS = 4_000L
        const val FORUM_OPTIMISTIC_BADGE_MS = 8_000L
        private const val CACHE_TTL_MS = 180_000L
    }
}
