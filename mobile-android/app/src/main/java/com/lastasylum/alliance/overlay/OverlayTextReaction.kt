package com.lastasylum.alliance.overlay

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Префикс id реакции для произвольного текста (сокет + вспышка). */
const val OVERLAY_TEXT_REACTION_PREFIX = "text:"

/** Лимит символов ввода и отображения (до 5 строк). */
const val OVERLAY_TEXT_REACTION_MAX_CHARS = 200

fun isTextReactionId(reactionId: String): Boolean =
    reactionId.startsWith(OVERLAY_TEXT_REACTION_PREFIX)

fun encodeTextReactionId(text: String): String? {
    val normalized = text
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(OVERLAY_TEXT_REACTION_MAX_CHARS)
    if (normalized.isEmpty()) return null
    val encoded = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(normalized.toByteArray(StandardCharsets.UTF_8))
    return OVERLAY_TEXT_REACTION_PREFIX + encoded
}

fun decodeTextReactionId(reactionId: String): String? {
    if (!isTextReactionId(reactionId)) return null
    val payload = reactionId.removePrefix(OVERLAY_TEXT_REACTION_PREFIX)
    if (payload.isEmpty()) return null
    return runCatching {
        val bytes = Base64.getUrlDecoder().decode(payload)
        String(bytes, StandardCharsets.UTF_8)
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(OVERLAY_TEXT_REACTION_MAX_CHARS)
            .takeIf { it.isNotEmpty() }
    }.getOrNull()
}
