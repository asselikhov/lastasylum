package com.lastasylum.alliance.overlay

/**
 * Inbound raid strip: only teammates actively in overlay ingame (or strip-eligible grace).
 * Outbound/self: strip eligibility only (optimistic send uses a separate path).
 */
object OverlayRaidStripIngestPolicy {
    fun shouldIngestInbound(
        overlayIngamePresenceActive: Boolean,
        stripEligible: Boolean,
    ): Boolean = overlayIngamePresenceActive || stripEligible

    fun shouldIngestOutbound(stripEligible: Boolean): Boolean = stripEligible
}
