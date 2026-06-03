package com.lastasylum.alliance.overlay

/**
 * Inbound raid strip: game-event notifies only while ingame (or grace); other raid traffic may
 * arrive on the overlay FGS socket while the listener is up.
 *
 * Outbound/self: strip eligibility only (optimistic send uses a separate path).
 */
object OverlayRaidStripIngestPolicy {
    fun shouldIngestInbound(
        overlayRealtimeListenerActive: Boolean,
        overlayStripEnabled: Boolean,
        overlayIngamePresenceActive: Boolean,
        stripEligible: Boolean,
        isGameEventNotify: Boolean = false,
    ): Boolean {
        if (!overlayStripEnabled) return false
        if (isGameEventNotify) {
            return overlayIngamePresenceActive || stripEligible
        }
        if (overlayRealtimeListenerActive) return true
        return overlayIngamePresenceActive || stripEligible
    }

    fun shouldIngestOutbound(stripEligible: Boolean): Boolean = stripEligible
}
