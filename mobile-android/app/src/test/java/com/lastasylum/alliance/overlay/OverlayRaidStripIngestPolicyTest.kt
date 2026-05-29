package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRaidStripIngestPolicyTest {
    @Test
    fun inbound_ingest_when_overlay_listener_active_without_ingame_probe() {
        assertTrue(
            OverlayRaidStripIngestPolicy.shouldIngestInbound(
                overlayRealtimeListenerActive = true,
                overlayStripEnabled = true,
                overlayIngamePresenceActive = false,
                stripEligible = false,
            ),
        )
    }

    @Test
    fun inbound_ingest_when_ingame_presence_active() {
        assertTrue(
            OverlayRaidStripIngestPolicy.shouldIngestInbound(
                overlayRealtimeListenerActive = false,
                overlayStripEnabled = true,
                overlayIngamePresenceActive = true,
                stripEligible = false,
            ),
        )
    }

    @Test
    fun inbound_ingest_when_strip_eligible_grace() {
        assertTrue(
            OverlayRaidStripIngestPolicy.shouldIngestInbound(
                overlayRealtimeListenerActive = false,
                overlayStripEnabled = true,
                overlayIngamePresenceActive = false,
                stripEligible = true,
            ),
        )
    }

    @Test
    fun inbound_drop_when_strip_disabled() {
        assertFalse(
            OverlayRaidStripIngestPolicy.shouldIngestInbound(
                overlayRealtimeListenerActive = true,
                overlayStripEnabled = false,
                overlayIngamePresenceActive = true,
                stripEligible = true,
            ),
        )
    }

    @Test
    fun inbound_drop_when_offline_overlay() {
        assertFalse(
            OverlayRaidStripIngestPolicy.shouldIngestInbound(
                overlayRealtimeListenerActive = false,
                overlayStripEnabled = true,
                overlayIngamePresenceActive = false,
                stripEligible = false,
            ),
        )
    }

    @Test
    fun outbound_only_when_strip_eligible() {
        assertTrue(OverlayRaidStripIngestPolicy.shouldIngestOutbound(stripEligible = true))
        assertFalse(OverlayRaidStripIngestPolicy.shouldIngestOutbound(stripEligible = false))
    }
}
