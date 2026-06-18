package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.InboxUnreadReconciler
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh

/** Pull forum read cursors from API and repair stale unread counts (reinstall-safe). */
object TeamForumReadCursorSync {
    suspend fun sync(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
    ) {
        repairOnly(teamsRepository, forumPrefs, teamId)
    }

    /** Repair stale server unread without overwriting device read cursors from server topics. */
    suspend fun repairOnly(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
    ) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val topics = teamsRepository.listForumTopics(tid).getOrNull() ?: return
        InboxUnreadReconciler.repairForumStaleUnread(teamsRepository, forumPrefs, tid, topics)
        OverlayGameStatusHudRefresh.invalidateForumCache()
    }
}
