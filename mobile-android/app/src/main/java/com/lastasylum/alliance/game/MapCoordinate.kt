package com.lastasylum.alliance.game

/**
 * Parsed map coordinates from raid chat / overlay messages.
 * Format tail: `X:{x} Y:{y}` with optional prefix (command label or target name).
 */
data class MapCoordinate(
    val label: String?,
    val x: Int,
    val y: Int,
) {
    fun coordinateSuffix(): String = "X:$x Y:$y"

    fun fullMessageText(): String =
        if (label.isNullOrBlank()) coordinateSuffix() else "${label.trim()} ${coordinateSuffix()}"
}

object MapCoordinateParser {
    private val COORD_TAIL = Regex("""X:(\d+)\s+Y:(\d+)\s*$""", RegexOption.IGNORE_CASE)

    /** Loose `123, 456` or `123/456` when the whole string is short. */
    private val LOOSE_PAIR = Regex("""^(\d+)\s*[,/]\s*(\d+)\s*$""")

    fun parse(text: String): MapCoordinate? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        COORD_TAIL.find(trimmed)?.let { match ->
            val x = match.groupValues[1].toIntOrNull() ?: return null
            val y = match.groupValues[2].toIntOrNull() ?: return null
            val prefix = trimmed.substring(0, match.range.first).trim()
            return MapCoordinate(prefix.takeIf { it.isNotEmpty() }, x, y)
        }
        LOOSE_PAIR.find(trimmed)?.let { match ->
            val x = match.groupValues[1].toIntOrNull() ?: return null
            val y = match.groupValues[2].toIntOrNull() ?: return null
            return MapCoordinate(null, x, y)
        }
        return null
    }

    /** Character range of `X:… Y:…` in [text], if present. */
    fun coordinateRangeIn(text: String): IntRange? {
        val trimmed = text.trimEnd()
        val coord = parse(trimmed) ?: return null
        val suffix = coord.coordinateSuffix()
        val start = trimmed.lastIndexOf(suffix, ignoreCase = true)
        if (start < 0) return null
        return start until (start + suffix.length)
    }

    /**
     * Parse text shared from another app (game share sheet).
     * Tries strict format first, then embedded coordinates in longer text.
     */
    fun parseSharedText(raw: String): MapCoordinate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        parse(trimmed)?.let { return it }
        val embedded = Regex("""X:(\d+)\s+Y:(\d+)""", RegexOption.IGNORE_CASE)
        embedded.find(trimmed)?.let { match ->
            val x = match.groupValues[1].toIntOrNull() ?: return null
            val y = match.groupValues[2].toIntOrNull() ?: return null
            val prefix = trimmed.substring(0, match.range.first).trim()
                .removePrefix("Target:")
                .trim()
            return MapCoordinate(prefix.takeIf { it.isNotEmpty() }, x, y)
        }
        return null
    }
}

object MapCoordinateFormatter {
    fun format(
        label: String?,
        targetName: String?,
        x: Int,
        y: Int,
    ): String {
        val name = targetName?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            name != null -> "$name X:$x Y:$y"
            !label.isNullOrBlank() -> "${label.trim()} X:$x Y:$y"
            else -> "X:$x Y:$y"
        }
    }

    fun formatExcavation(excavationTemplate: String, x: Int, y: Int): String =
        excavationTemplate.format(x, y)
}
