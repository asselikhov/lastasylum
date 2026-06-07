package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.users.UsersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Overlay ingame heartbeat and away transitions while [CombatOverlayService] runs.
 */
internal class OverlayPresenceCoordinator(
    private val scope: CoroutineScope,
    private val usersRepository: UsersRepository,
    private val isOverlayEnabled: () -> Boolean,
    private val hasSession: () -> Boolean,
    private val isInGameOverlayUiActive: () -> Boolean,
    private val isOverlaySessionActive: () -> Boolean,
    private val isVoiceActive: () -> Boolean,
    private val isOnlineParticipantsPanelVisible: () -> Boolean,
    private val canUseOverlayVoiceNow: () -> Boolean,
    private val onStopVoice: () -> Unit,
) {
    private val heartbeat = OverlayPresenceHeartbeat(
        scope = scope,
        intervalMs = HEARTBEAT_INTERVAL_MS,
        ping = {
            usersRepository.updatePresence(PRESENCE_INGAME)
            Unit
        },
    )

    var ingamePresenceActive: Boolean = false
        private set

    private var missStreak = 0

    fun sync(inGameProbe: Boolean) {
        if (!isOverlayEnabled() || !hasSession()) {
            missStreak = 0
            stop(markAway = false)
            return
        }
        val keepIngamePing = computeOverlayKeepIngamePing(
            inGameProbe = inGameProbe,
            overlaySessionActive = isOverlaySessionActive(),
            inGameOverlayUiActive = isInGameOverlayUiActive(),
            isVoiceActive = isVoiceActive(),
            isOnlineParticipantsPanelVisible = isOnlineParticipantsPanelVisible(),
        )
        if (keepIngamePing) {
            missStreak = 0
            val firstStart = !ingamePresenceActive
            ingamePresenceActive = true
            heartbeat.start()
            if (firstStart) {
                OverlayRuntimeMetrics.logPresenceTransition("idle", PRESENCE_INGAME, inGameProbe)
            }
            if (firstStart || isVoiceActive()) {
                scope.launch {
                    runCatching {
                        usersRepository.updatePresence(PRESENCE_INGAME)
                    }
                }
            }
            return
        }
        heartbeat.stop()
        missStreak++
        if (missStreak < AWAY_MISS_STREAK) {
            return
        }
        if (isOverlaySessionActive()) {
            return
        }
        stop(markAway = true)
    }

    /** Immediate ingame ping when overlay session starts (before first heartbeat tick). */
    fun pingIngameImmediate() {
        if (!isOverlayEnabled() || !hasSession()) return
        scope.launch {
            runCatching {
                usersRepository.updatePresence(PRESENCE_INGAME)
            }
        }
    }

    fun stop(markAway: Boolean) {
        heartbeat.stop()
        if (ingamePresenceActive) {
            OverlayRuntimeMetrics.logPresenceTransition(
                PRESENCE_INGAME,
                if (markAway) PRESENCE_AWAY else "idle",
                inGameProbe = false,
            )
        }
        ingamePresenceActive = false
        if (markAway && !canUseOverlayVoiceNow()) {
            onStopVoice()
        }
        if (!markAway || !hasSession()) return
        scope.launch {
            runCatching {
                usersRepository.updatePresence(PRESENCE_AWAY)
            }
        }
    }

    private companion object {
        private const val HEARTBEAT_INTERVAL_MS = 45_000L
        private const val AWAY_MISS_STREAK = 3
        private const val PRESENCE_INGAME = "ingame"
        private const val PRESENCE_AWAY = "away"
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun computeOverlayKeepIngamePing(
    inGameProbe: Boolean,
    overlaySessionActive: Boolean,
    inGameOverlayUiActive: Boolean,
    isVoiceActive: Boolean,
    isOnlineParticipantsPanelVisible: Boolean,
): Boolean =
    inGameProbe ||
        inGameOverlayUiActive ||
        isVoiceActive ||
        isOnlineParticipantsPanelVisible
