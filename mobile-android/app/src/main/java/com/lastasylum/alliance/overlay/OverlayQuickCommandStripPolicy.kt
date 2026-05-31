package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.game.MapCoordinateParser
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Suppresses raid strip cards for the sender of overlay quick commands (Attack/Storm/Reinf/Excavation).
 * Chat room «Рейд» is unaffected — only the top overlay strip is hidden for own sends.
 */
internal object OverlayQuickCommandStripPolicy {
    private const val SUPPRESS_TTL_MS = 15_000L

    /** Alliance excavation notify without coordinates. */
    private fun isExcavationNotify(text: String): Boolean =
        text.lowercase(Locale.ROOT) == "раскопки альянса"

    private val suppressUntilByText = ConcurrentHashMap<String, Long>()

    fun markOutgoingQuickCommand(text: String) {
        val key = normalizeText(text)
        if (key.isEmpty()) return
        suppressUntilByText[key] = System.currentTimeMillis() + SUPPRESS_TTL_MS
    }

    fun clearOutgoingQuickCommand(text: String) {
        val key = normalizeText(text)
        if (key.isEmpty()) return
        suppressUntilByText.remove(key)
    }

    fun shouldSuppressOwnStripCard(text: String): Boolean {
        val key = normalizeText(text)
        if (key.isEmpty()) return false
        pruneExpired()
        val until = suppressUntilByText[key] ?: return isQuickCommandShape(key)
        return System.currentTimeMillis() < until
    }

    /** Detects coordinate quick commands and excavation notify without coords. */
    fun isQuickCommandShape(text: String): Boolean {
        val t = normalizeText(text)
        if (t.isEmpty()) return false
        if (MapCoordinateParser.parse(t) != null) return true
        return isExcavationNotify(t)
    }

    private fun normalizeText(text: String): String = text.trim()

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        suppressUntilByText.entries.removeIf { it.value <= now }
    }

    /** Test-only: reset state between unit tests. */
    internal fun clearForTests() {
        suppressUntilByText.clear()
    }
}
