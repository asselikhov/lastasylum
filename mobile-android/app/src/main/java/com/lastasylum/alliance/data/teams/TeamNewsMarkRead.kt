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
        var cursor: String? = null
        var maxCreatedAt: Instant? = null
        var maxCreatedAtIso: String? = null
        do {
            val page = teamsRepository.listTeamNews(tid, cursor = cursor, limit = 100)
                .getOrNull() ?: break
            if (page.items.isEmpty()) break
            page.items.forEach { item ->
                val parsed = runCatching { Instant.parse(item.createdAt.trim()) }.getOrNull() ?: return@forEach
                if (maxCreatedAt == null || parsed.isAfter(maxCreatedAt)) {
                    maxCreatedAt = parsed
                    maxCreatedAtIso = item.createdAt.trim()
                }
            }
            cursor = page.nextCursor?.trim()?.takeIf { it.isNotEmpty() }
        } while (cursor != null)
        if (!maxCreatedAtIso.isNullOrBlank()) {
            TeamNewsReadCursorSync.markNewsSeen(teamsRepository, prefs, tid, maxCreatedAtIso)
            return
        }
        val serverIso = teamsRepository.getTeamNewsReadCursor(tid).getOrNull()
            ?.lastSeenCreatedAt
            ?.trim()
            .orEmpty()
        if (serverIso.isNotEmpty()) {
            TeamNewsReadCursorSync.markNewsSeen(teamsRepository, prefs, tid, serverIso)
        }
    }
}
