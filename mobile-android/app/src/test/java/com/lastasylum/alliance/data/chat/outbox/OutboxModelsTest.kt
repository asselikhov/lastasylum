package com.lastasylum.alliance.data.chat.outbox

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxModelsTest {
    @Test
    fun outboxSendState_roundTripsWire() {
        assertEquals(OutboxSendState.Pending, OutboxSendState.fromWire("pending"))
        assertEquals(OutboxSendState.Failed, OutboxSendState.fromWire("failed"))
    }
}
