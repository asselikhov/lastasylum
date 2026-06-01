package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sync team news read cursor with server (reinstall-safe). */
object TeamNewsReadCursorSync {
    private const val MARK_SEEN_DEBOUNCE_MS = 320L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var markSeenJob: Job? = null

    @Volatile
    private var pendingSeenIso: String? = null

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

    /** Opening a post — advance cursor immediately (detail screen). */
    suspend fun markNewsSeen(
        teamsRepository: TeamsRepository,
        prefs: UserSettingsPreferences,
        teamId: String,
        createdAt: String?,
    ) {
        markSeenJob?.cancel()
        pendingSeenIso = null
        val iso = createdAt?.trim().orEmpty()
        if (iso.isEmpty()) return
        mergeSeenAtIntoPrefs(iso, prefs)
        val tid = teamId.trim()
        if (tid.isNotEmpty()) {
            teamsRepository.advanceTeamNewsReadCursor(tid, iso)
        }
        CombatOverlayService.notifyOverlayTeamInboxChanged(news = true)
    }

    /** Overlay feed: advance local cursor while scrolling; debounced server PATCH. */
    fun markNewsSeenUpTo(
        teamsRepository: TeamsRepository,
        prefs: UserSettingsPreferences,
        teamId: String,
        createdAt: String?,
    ) {
        val iso = createdAt?.trim().orEmpty()
        if (iso.isEmpty()) return
        val incoming = runCatching { Instant.parse(iso) }.getOrNull() ?: return
        val prevIso = prefs.getLastSeenTeamNewsCreatedAt()
        val prev = prevIso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (prev != null && !incoming.isAfter(prev)) return
        mergeSeenAtIntoPrefs(iso, prefs)
        pendingSeenIso = iso
        markSeenJob?.cancel()
        markSeenJob = scope.launch {
            delay(MARK_SEEN_DEBOUNCE_MS)
            flushPendingNewsCursor(teamsRepository, prefs, teamId)
        }
    }

    suspend fun flushPendingNewsCursor(
        teamsRepository: TeamsRepository,
        prefs: UserSettingsPreferences,
        teamId: String,
    ) {
        markSeenJob?.cancel()
        markSeenJob = null
        val iso = pendingSeenIso?.trim()?.takeIf { it.isNotEmpty() }
            ?: prefs.getLastSeenTeamNewsCreatedAt()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        pendingSeenIso = null
        pushPrefsToServerIfNewer(teamsRepository, prefs, teamId)
        CombatOverlayService.notifyOverlayTeamInboxChanged(news = true)
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
