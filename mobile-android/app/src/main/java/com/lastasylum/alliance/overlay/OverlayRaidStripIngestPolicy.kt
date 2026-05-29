package com.lastasylum.alliance.overlay

/**
 * Inbound raid strip: show cards for teammates with an active overlay FGS socket listener
 * (message already delivered). Presence/ingame gates are for backend fan-out, not for
 * dropping inbound [message:new] on the client.
 *
 * Outbound/self: strip eligibility only (optimistic send uses a separate path).
 */
object OverlayRaidStripIngestPolicy {
    fun shouldIngestInbound(
        overlayRealtimeListenerActive: Boolean,
        overlayStripEnabled: Boolean,
        overlayIngamePresenceActive: Boolean,
        stripEligible: Boolean,
    ): Boolean {
        if (!overlayStripEnabled) return false
        if (overlayRealtimeListenerActive) return true
        return overlayIngamePresenceActive || stripEligible
    }

    fun shouldIngestOutbound(stripEligible: Boolean): Boolean = stripEligible
}
