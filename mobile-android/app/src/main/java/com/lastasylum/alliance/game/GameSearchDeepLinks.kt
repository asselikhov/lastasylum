package com.lastasylum.alliance.game

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * [globalphslink] URLs for in-game search / profile / map.
 *
 * Confirmed via Frida [InvokeDeepLinkActivated] on device (Frida Gadget, v1.0.77):
 * - Query strings with `&` are truncated at the first ampersand (e.g. `map?x=512&y=384` → `map?x=512`).
 * - Path-style and single-parameter URLs are delivered intact.
 * - Clipboard `X:{x} Y:{y}` is used alongside map deep links as a fallback.
 */
object GameSearchDeepLinks {
    private fun enc(value: String): String =
        URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun encPath(value: String): String = enc(value).replace("%2F", "%252F")

    private fun serverPath(serverNumber: Int?): String =
        serverNumber?.takeIf { it in 1..9999 }?.let { "/$it" }.orEmpty()

    /** Opens in-game search UI; name is also copied to clipboard by [GameSearchBridge]. */
    fun searchUrls(
        kind: GameSearchBridge.SearchKind,
        name: String,
        serverNumber: Int?,
    ): List<String> {
        val n = encPath(name)
        val type = when (kind) {
            GameSearchBridge.SearchKind.PLAYER -> "player"
            GameSearchBridge.SearchKind.ALLIANCE -> "alliance"
        }
        val s = serverPath(serverNumber)
        return listOf(
            "globalphslink://search/$type/$n$s",
            "globalphslink://search/$type/$n",
            "globalphslink://$type/search/$n$s",
            "globalphslink://search",
            "globalphslink://search?type=$type",
            "globalphslink://search?$type=$n",
        )
    }

    fun profileUrls(
        kind: GameSearchBridge.SearchKind,
        name: String,
        serverNumber: Int?,
    ): List<String> {
        val n = encPath(name)
        val type = when (kind) {
            GameSearchBridge.SearchKind.PLAYER -> "player"
            GameSearchBridge.SearchKind.ALLIANCE -> "alliance"
        }
        val s = serverPath(serverNumber)
        return listOf(
            "globalphslink://profile/$type/$n$s",
            "globalphslink://profile/$type/$n",
            "globalphslink://$type/profile/$n$s",
            "globalphslink://$type/$n$s",
            "globalphslink://role/$n$s",
            "globalphslink://profile?type=$type",
            "globalphslink://profile?$type=$n",
        )
    }

    fun mapUrlsForCoordinates(x: Int, y: Int, serverNumber: Int?): List<String> {
        val s = serverPath(serverNumber)
        return listOf(
            // Path-style first: generic `map`/`world` resolve but do not fly to X/Y.
            "globalphslink://map/$x/$y$s",
            "globalphslink://world/$x/$y$s",
            "globalphslink://coordinate/$x/$y$s",
            "globalphslink://map/$x/$y",
            "globalphslink://world/$x/$y",
            "globalphslink://coordinate/$x/$y",
            "globalphslink://map?xy=$x,$y",
            "globalphslink://world?xy=$x,$y",
            "globalphslink://coordinate?xy=$x,$y",
            "globalphslink://coordinate?$x,$y",
            "globalphslink://map?x=$x",
            // Clipboard `X:{x} Y:{y}` + generic trigger — fallback after explicit coords.
            "globalphslink://map",
            "globalphslink://world",
        )
    }

    fun mapUrlsForName(
        kind: GameSearchBridge.SearchKind,
        name: String,
        serverNumber: Int?,
    ): List<String> {
        val n = encPath(name)
        val type = when (kind) {
            GameSearchBridge.SearchKind.PLAYER -> "player"
            GameSearchBridge.SearchKind.ALLIANCE -> "alliance"
        }
        val s = serverPath(serverNumber)
        return listOf(
            "globalphslink://map/$type/$n$s",
            "globalphslink://world/$type/$n$s",
            "globalphslink://goto/$type/$n$s",
            "globalphslink://map/$type/$n",
            "globalphslink://map?type=$type",
            "globalphslink://world",
            "globalphslink://map",
        )
    }
}
