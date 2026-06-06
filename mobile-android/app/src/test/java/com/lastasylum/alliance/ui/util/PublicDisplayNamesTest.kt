package com.lastasylum.alliance.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicDisplayNamesTest {
    @Test
    fun looksLikeAccountEmail_detectsValidEmail() {
        assertTrue(looksLikeAccountEmail("user@example.com"))
    }

    @Test
    fun looksLikeAccountEmail_rejectsNicknameWithAt() {
        assertFalse(looksLikeAccountEmail("player@home"))
    }

    @Test
    fun looksLikeAccountEmail_rejectsPlainNickname() {
        assertFalse(looksLikeAccountEmail("Pilot"))
    }

    @Test
    fun sanitizePublicDisplayName_replacesEmailWithFallback() {
        assertEquals("Союзник", sanitizePublicDisplayName("user@example.com"))
    }

    @Test
    fun sanitizePublicDisplayName_keepsNickname() {
        assertEquals("Pilot", sanitizePublicDisplayName("Pilot"))
    }

    @Test
    fun sanitizePublicDisplayName_usesCustomFallback() {
        assertEquals("Автор", sanitizePublicDisplayName("a@b.c", fallback = "Автор"))
    }
}
