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

    @Volatile
    private var pendingTeamId: String? = null

    fun cancelPendingJobs() {
        markSeenJob?.cancel()
        markSeenJob = null
        pendingSeenIso = null
        pendingTeamId = null
    }

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
        mergeSeenAtIntoPrefs(serverIso, prefs, tid)
    }

    suspend fun pushPrefsToServerIfNewer(
        teamsRepository: TeamsRepository,
        prefs: UserSettingsPreferences,
        teamId: String,
    ) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val localIso = prefs.getLastSeenTeamNewsCreatedAt(tid)?.trim().orEmpty()
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
        val tid = teamId.trim()
        if (tid.isNotEmpty()) {
            flushPendingNewsCursor(teamsRepository, prefs, tid)
        }
        val iso = createdAt?.trim().orEmpty()
        if (iso.isEmpty()) return
        val localIso = prefs.getLastSeenTeamNewsCreatedAt(tid)?.trim().orEmpty()
        val effectiveIso = maxSeenIso(iso, localIso) ?: iso
        mergeSeenAtIntoPrefs(effectiveIso, prefs, teamId)
        if (tid.isNotEmpty()) {
            teamsRepository.advanceTeamNewsReadCursor(tid, effectiveIso)
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
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val iso = createdAt?.trim().orEmpty()
        if (iso.isEmpty()) return
        val incoming = runCatching { Instant.parse(iso) }.getOrNull() ?: return
        val prevIso = prefs.getLastSeenTeamNewsCreatedAt(tid)
        val prev = prevIso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (prev != null && !incoming.isAfter(prev)) return
        mergeSeenAtIntoPrefs(iso, prefs, tid)
        pendingSeenIso = iso
        pendingTeamId = tid
        markSeenJob?.cancel()
        markSeenJob = scope.launch {
            delay(MARK_SEEN_DEBOUNCE_MS)
            flushPendingNewsCursor(teamsRepository, prefs, tid)
        }
    }

    suspend fun flushPendingNewsCursor(
        teamsRepository: TeamsRepository,
        prefs: UserSettingsPreferences,
        teamId: String,
    ) {
        markSeenJob?.cancel()
        markSeenJob = null
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val iso = pendingSeenIso?.trim()?.takeIf { it.isNotEmpty() }
            ?: prefs.getLastSeenTeamNewsCreatedAt(tid)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        pendingSeenIso = null
        pendingTeamId = null
        pushPrefsToServerIfNewer(teamsRepository, prefs, tid)
        CombatOverlayService.notifyOverlayTeamInboxChanged(news = true)
    }

    private fun mergeSeenAtIntoPrefs(iso: String, prefs: UserSettingsPreferences, teamId: String) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val incoming = runCatching { Instant.parse(iso) }.getOrNull() ?: return
        val prevIso = prefs.getLastSeenTeamNewsCreatedAt(tid)
        val prev = prevIso?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (prev == null || incoming.isAfter(prev)) {
            prefs.setLastSeenTeamNewsCreatedAt(tid, iso)
            OverlayGameStatusHudRefresh.invalidateNewsCache()
            CombatOverlayService.notifyOverlayTeamInboxChanged(news = true)
        }
    }

    private fun maxSeenIso(vararg candidates: String): String? =
        candidates.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { iso ->
                runCatching { Instant.parse(iso) }.getOrNull()?.let { parsed -> iso to parsed }
            }
            .maxByOrNull { it.second }
            ?.first
}
