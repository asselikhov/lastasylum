package com.lastasylum.alliance.data.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatRoomPreferencesTest {
    private lateinit var prefs: ChatRoomPreferences

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("squadrelay_chat", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = ChatRoomPreferences(ctx)
        prefs.bindUser("user-a")
        prefs.setRaidRoomId("raid-room")
        prefs.setHubRoomId("hub-room")
        prefs.setSelectedRoomId("selected-room")
        prefs.setLastReadMessageId("room-1", "msg-old")
    }

    @Test
    fun clearLastReadCursors_keepsRoomIdPrefs() {
        prefs.clearLastReadCursors()
        assertNull(prefs.getLastReadMessageId("room-1"))
        assertEquals("raid-room", prefs.getRaidRoomId())
        assertEquals("hub-room", prefs.getHubRoomId())
        assertEquals("selected-room", prefs.getSelectedRoomId())
    }
}
