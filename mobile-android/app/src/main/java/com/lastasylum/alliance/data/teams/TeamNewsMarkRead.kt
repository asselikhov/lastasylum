package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import java.time.Instant

/** Mark every loaded team news item as seen (overlay «прочитать всё»). */
object TeamNewsMarkRead {
    suspend fun markAllNewsRead(
        teamsRepository: TeamsRepository,
        prefs: UserSettingsPreferences,
        teamId: String,
        currentUserId: String = "",
    ) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val items = teamsRepository.listTeamNews(tid, cursor = null, limit = 100)
            .getOrNull()
            ?.items
            .orEmpty()
        if (items.isEmpty()) {
            val serverIso = teamsRepository.getTeamNewsReadCursor(tid).getOrNull()
                ?.lastSeenCreatedAt
                ?.trim()
                .orEmpty()
            if (serverIso.isNotEmpty()) {
                TeamNewsReadCursorSync.markNewsSeen(teamsRepository, prefs, tid, serverIso)
            }
            return
        }
        val maxCreatedAt = items.mapNotNull { item ->
            runCatching { Instant.parse(item.createdAt.trim()) }.getOrNull()
                ?.let { parsed -> item.createdAt.trim() to parsed }
        }.maxByOrNull { it.second }?.first
        if (maxCreatedAt.isNullOrBlank()) return
        TeamNewsReadCursorSync.markNewsSeen(teamsRepository, prefs, tid, maxCreatedAt)
    }
}
