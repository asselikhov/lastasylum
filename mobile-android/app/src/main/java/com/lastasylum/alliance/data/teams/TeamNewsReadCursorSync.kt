package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh
import java.time.Instant

/** Sync team news read cursor with server (reinstall-safe). */
object TeamNewsReadCursorSync {
    suspend fun pullServerCursorIntoPrefs(
        teamsRepository: TeamsRepository,
        prefs: UserSettingsPreferences,
        teamId: String,
    ) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val serverIso = teamsRepository.getTeamNewsReadCursor(tid).getOrNull()
            ?.lastSeenCreatedAt
            ?.trim()
            .orEmpty()
        if (serverIso.isEmpty()) return
        mergeSeenAtIntoPrefs(serverIso, prefs)
    }

    suspend fun pushPrefsToServerIfNewer(
        teamsRepository: TeamsRepository,
        prefs: UserSettingsPreferences,
        teamId: String,
    ) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val localIso = prefs.getLastSeenTeamNewsCreatedAt()?.trim().orEmpty()
        if (localIso.isEmpty()) return
        val serverIso = teamsRepository.getTeamNewsReadCursor(tid).getOrNull()
            ?.lastSeenCreatedAt
            ?.trim()
            .orEmpty()
        if (serverIso.isNotEmpty()) {
            val local = runCatching { Instant.parse(localIso) }.getOrNull() ?: return
            val server = runCatching { Instant.parse(serverIso) }.getOrNull() ?: return
            if (!local.isAfter(server)) return
        }
        teamsRepository.advanceTeamNewsReadCursor(tid, localIso)
    }

    suspend fun markNewsSeen(
        teamsRepository: TeamsRepository,
        prefs: UserSettingsPreferences,
        teamId: String,
        createdAt: String?,
    ) {
        OverlayGameStatusHudRefresh.markTeamNewsSeenAt(createdAt, prefs)
        CombatOverlayService.notifyOverlayTeamInboxChanged(news = true)
        val iso = createdAt?.trim().orEmpty()
        val tid = teamId.trim()
        if (iso.isEmpty() || tid.isEmpty()) return
        teamsRepository.advanceTeamNewsReadCursor(tid, iso)
    }

    private fun mergeSeenAtIntoPrefs(iso: String, prefs: UserSettingsPreferences) {
        val incoming = runCatching { Instant.parse(iso) }.getOrNull() ?: return
        val prevIso = prefs.getLastSeenTeamNewsCreatedAt()
        val prev = prevIso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (prev == null || incoming.isAfter(prev)) {
            prefs.setLastSeenTeamNewsCreatedAt(iso)
            OverlayGameStatusHudRefresh.invalidateNewsCache()
            CombatOverlayService.notifyOverlayTeamInboxChanged(news = true)
        }
    }
}
