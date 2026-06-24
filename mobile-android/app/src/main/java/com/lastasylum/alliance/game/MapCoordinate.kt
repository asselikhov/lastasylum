package com.lastasylum.alliance.game

/**
 * Parsed map coordinates from raid chat / overlay / in-game share messages.
 *
 * SquadRelay quick commands: `{label} X:{x} Y:{y}`.
 * Last Asylum in-game share: `[#:{server} X:{x} Y:{y}]` (server = kingdom/world id on the map).
 */
data class MapCoordinate(
    val label: String?,
    val x: Int,
    val y: Int,
    val serverNumber: Int? = null,
) {
    fun coordinateSuffix(): String =
        if (serverNumber != null) {
            "#:$serverNumber X:$x Y:$y"
        } else {
            "X:$x Y:$y"
        }

    /** In-game bracket form for clipboard / display when server is known. */
    fun gameBracketText(): String =
        if (serverNumber != null) {
            "[#:$serverNumber X:$x Y:$y]"
        } else {
            "X:$x Y:$y"
        }

    /** flyWorldLua reads `X:{x} Y:{y}` from clipboard (Frida RE, v1.0.77). */
    fun mapClipboardText(): String = "X:$x Y:$y"

    fun fullMessageText(): String =
        if (label.isNullOrBlank()) gameBracketText() else "${label.trim()} ${gameBracketText()}"
}

object MapCoordinateParser {
    /** Last Asylum share: `[#:109 X:505 Y:495]` or `#:109 X:505 Y:495`. */
    private val GAME_COORD = Regex(
        """\[?#:(\d+)\s+X:(\d+)\s+Y:(\d+)\]?""",
        RegexOption.IGNORE_CASE,
    )

    private val COORD_TAIL = Regex("""X:(\d+)\s+Y:(\d+)\s*$""", RegexOption.IGNORE_CASE)

    /** Loose `123, 456` or `123/456` when the whole string is short. */
    private val LOOSE_PAIR = Regex("""^(\d+)\s*[,/]\s*(\d+)\s*$""")

    fun parse(text: String): MapCoordinate? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        parseGameCoord(trimmed)?.let { return it }
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

    /** Character range of the clickable coordinate block in [text], if present. */
    fun coordinateRangeIn(text: String): IntRange? {
        val trimmed = text.trimEnd()
        gameCoordRangeIn(trimmed)?.let { return it }
        val coord = parse(trimmed) ?: return null
        val suffix = coord.coordinateSuffix()
        val start = trimmed.lastIndexOf(suffix, ignoreCase = true)
        if (start < 0) return null
        return start until (start + suffix.length)
    }

    /**
     * Parse text shared from another app (game share sheet).
     * Tries in-game bracket format, strict tail, then embedded coordinates.
     */
    fun parseSharedText(raw: String): MapCoordinate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        parse(trimmed)?.let { return it }
        parseGameCoord(trimmed)?.let { return it }
        val embeddedGame = GAME_COORD.find(trimmed)
        if (embeddedGame != null) {
            return coordFromGameMatch(trimmed, embeddedGame)
        }
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

    private fun parseGameCoord(text: String): MapCoordinate? {
        val match = GAME_COORD.findAll(text).lastOrNull() ?: return null
        return coordFromGameMatch(text, match)
    }

    private fun gameCoordRangeIn(text: String): IntRange? =
        GAME_COORD.findAll(text).lastOrNull()?.range

    private fun coordFromGameMatch(text: String, match: MatchResult): MapCoordinate? {
        val server = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..9999 } ?: return null
        val x = match.groupValues[2].toIntOrNull() ?: return null
        val y = match.groupValues[3].toIntOrNull() ?: return null
        val prefix = text.substring(0, match.range.first).trim()
        return MapCoordinate(
            label = prefix.takeIf { it.isNotEmpty() },
            x = x,
            y = y,
            serverNumber = server,
        )
    }
}

object MapCoordinateFormatter {
    fun format(
        label: String?,
        targetName: String?,
        x: Int,
        y: Int,
        serverNumber: Int? = null,
        coordsOnNewLine: Boolean = false,
    ): String {
        val coords = MapCoordinate(null, x, y, serverNumber).let { c ->
            if (serverNumber != null) c.gameBracketText() else c.coordinateSuffix()
        }
        val name = targetName?.trim()?.takeIf { it.isNotEmpty() }
        val sep = if (coordsOnNewLine) "\n" else " "
        return when {
            name != null -> "$name$sep$coords"
            !label.isNullOrBlank() -> "${label.trim()}$sep$coords"
            else -> coords
        }
    }

    fun formatExcavation(excavationTemplate: String, x: Int, y: Int): String =
        excavationTemplate.format(x, y)
}
