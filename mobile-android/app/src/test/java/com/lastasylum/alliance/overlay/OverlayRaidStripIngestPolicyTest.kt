package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRaidStripIngestPolicyTest {
    @Test
    fun inbound_ingest_when_ingame_presence_active() {
        assertTrue(
            OverlayRaidStripIngestPolicy.shouldIngestInbound(
                overlayIngamePresenceActive = true,
                stripEligible = false,
            ),
        )
    }

    @Test
    fun inbound_ingest_when_strip_eligible_grace() {
        assertTrue(
            OverlayRaidStripIngestPolicy.shouldIngestInbound(
                overlayIngamePresenceActive = false,
                stripEligible = true,
            ),
        )
    }

    @Test
    fun inbound_drop_when_offline_overlay() {
        assertFalse(
            OverlayRaidStripIngestPolicy.shouldIngestInbound(
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
