package com.lastasylum.alliance.data.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PinHistoryPreferencesTest {
    private lateinit var prefs: PinHistoryPreferences

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = PinHistoryPreferences(ctx)
        prefs.bindUser("user-test")
        prefs.clearUser()
        prefs.bindUser("user-test")
    }

    @Test
    fun saveAndLoad_roundTripsPreviewList() {
        val scope = prefs.chatScopeKey("room-1")
        val entries = listOf(
            PinnedMessagePreviewDto(
                id = "507f1f77bcf86cd799439011",
                text = "one",
                senderUsername = "alice",
                createdAt = "2026-01-01T00:00:00Z",
                imageThumbnailUrl = "/img/1.jpg",
                pinnedByUsername = "bob",
            ),
            PinnedMessagePreviewDto(
                id = "507f1f77bcf86cd799439012",
                text = "two",
                senderUsername = "alice",
                createdAt = "2026-01-02T00:00:00Z",
            ),
        )
        prefs.save(scope, entries)
        val loaded = prefs.load(scope)
        assertEquals(2, loaded.size)
        assertEquals("one", loaded[0].text)
        assertEquals("/img/1.jpg", loaded[0].imageThumbnailUrl)
        assertEquals("bob", loaded[0].pinnedByUsername)
        assertEquals("507f1f77bcf86cd799439012", loaded[1].id)
    }
}
