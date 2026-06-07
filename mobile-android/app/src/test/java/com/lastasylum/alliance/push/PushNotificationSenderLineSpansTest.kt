package com.lastasylum.alliance.push

import android.text.style.ForegroundColorSpan
import com.lastasylum.alliance.ui.chat.ChatSenderLineColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PushNotificationSenderLineSpansTest {
    @Test
    fun build_usesRoleAccentForNickname() {
        val line = PushNotificationSenderLineSpans.build(
            teamTag = "OBZ",
            username = "Alice",
            serverNumber = 109,
            squadRole = "R5",
        )
        val spannable = line as android.text.SpannableString
        val nickColor = ChatSenderLineColors.nicknameColorArgb("R5")
        val spans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
        assertTrue(spans.any { it.foregroundColor == nickColor })
    }

    @Test
    fun build_usesThemePrimaryForServer() {
        val line = PushNotificationSenderLineSpans.build(
            teamTag = null,
            username = "Bob",
            serverNumber = 42,
            squadRole = "R1",
        )
        val spannable = line as android.text.SpannableString
        val serverColor = ChatSenderLineColors.serverColorArgb()
        val spans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
        assertTrue(spans.any { it.foregroundColor == serverColor })
    }

    @Test
    fun build_usesTagColor() {
        val line = PushNotificationSenderLineSpans.build(
            teamTag = "TAG",
            username = "Carol",
            serverNumber = null,
            squadRole = "R3",
        )
        val spannable = line as android.text.SpannableString
        val tagColor = ChatSenderLineColors.tagColorArgb()
        val spans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
        assertTrue(spans.any { it.foregroundColor == tagColor })
        assertEquals(0xFF82B1FF.toInt(), ChatSenderLineColors.nicknameColorArgb("R3"))
    }
}
