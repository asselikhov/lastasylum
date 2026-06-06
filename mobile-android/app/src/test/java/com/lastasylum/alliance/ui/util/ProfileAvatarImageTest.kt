package com.lastasylum.alliance.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileAvatarImageTest {
    @Test
    fun resolvedProfileAvatarUrl_buildsAbsoluteFromRelative() {
        val url = resolvedProfileAvatarUrl("/users/avatars/user1?v=123")
        assertEquals(true, url?.endsWith("/users/avatars/user1?v=123") == true)
    }

    @Test
    fun resolvedProfileAvatarUrl_returnsNullForBlank() {
        assertNull(resolvedProfileAvatarUrl(null))
        assertNull(resolvedProfileAvatarUrl("   "))
    }

    @Test
    fun resolvedProfileAvatarUrl_keepsHttpUrls() {
        assertEquals(
            "https://cdn.example.com/a.png",
            resolvedProfileAvatarUrl("https://cdn.example.com/a.png"),
        )
    }
}
