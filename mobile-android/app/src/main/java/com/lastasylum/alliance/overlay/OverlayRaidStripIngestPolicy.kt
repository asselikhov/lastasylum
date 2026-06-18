package com.lastasylum.alliance.overlay

/**
 * Inbound raid strip: only while ingame (or short grace). Socket listener alone does not qualify.
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
        return overlayIngamePresenceActive || stripEligible
    }

    fun shouldIngestOutbound(stripEligible: Boolean): Boolean = stripEligible
}
