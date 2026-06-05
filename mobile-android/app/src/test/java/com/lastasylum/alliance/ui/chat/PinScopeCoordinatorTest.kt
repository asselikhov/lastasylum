package com.lastasylum.alliance.ui.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.PinHistoryPreferences
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PinScopeCoordinatorTest {
    private lateinit var prefs: PinHistoryPreferences
    private lateinit var coordinator: PinScopeCoordinator

    private val previewA = PinnedMessagePreviewDto(
        id = "507f1f77bcf86cd799439014",
        text = "first",
        senderUsername = "alice",
        createdAt = "2026-01-01T00:00:00Z",
    )
    private val previewB = PinnedMessagePreviewDto(
        id = "507f1f77bcf86cd799439015",
        text = "second",
        senderUsername = "bob",
        createdAt = "2026-01-02T00:00:00Z",
    )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = PinHistoryPreferences(ctx)
        prefs.bindUser("pin-scope-test")
        coordinator = PinScopeCoordinator(prefs, prefs.chatScopeKey("room1"))
        coordinator.pinnedMessageId = previewA.id
        coordinator.pinnedMessage = previewA
        coordinator.replacePinHistory(listOf(previewA, previewB))
    }

    @Test
    fun dismissPinBar_hidesBarPreviewButKeepsHistoryCount() {
        coordinator.applyPinBarUi(emptyList())
        assertEquals(2, coordinator.pinHistoryCount)
        coordinator.dismissPinBar()
        coordinator.applyPinBarUi(emptyList())
        assertNull(coordinator.pinBarPreview)
        assertEquals(2, coordinator.pinHistoryCount)
    }

    @Test
    fun restorePinBar_showsBarAgain() {
        coordinator.dismissPinBar()
        coordinator.applyPinBarUi(emptyList())
        assertNull(coordinator.pinBarPreview)
        coordinator.restorePinBar()
        coordinator.applyPinBarUi(emptyList())
        assertEquals(previewA.id, coordinator.pinBarPreview?.id)
    }
}
