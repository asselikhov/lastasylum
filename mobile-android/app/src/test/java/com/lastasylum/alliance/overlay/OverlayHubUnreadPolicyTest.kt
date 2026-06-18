package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayHubUnreadPolicyTest {
    @Test
    fun reconcileGrace_isTwoSeconds() {
        assertEquals(2_000L, OverlayHubUnreadPolicy.RECONCILE_GRACE_MS)
    }
}
