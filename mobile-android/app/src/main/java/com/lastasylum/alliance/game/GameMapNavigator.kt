package com.lastasylum.alliance.game

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lastasylum.alliance.R

/**
 * Opens the configured target game at map coordinates: clipboard, delay, deep link.
 */
object GameMapNavigator {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun open(context: Context, x: Int, y: Int, serverNumber: Int? = null) {
        GameDeepLinkNavigator.openWithClipboard(
            context = context,
            clipLabel = "map_coordinates",
            clipText = "X:$x Y:$y",
            uris = GameSearchDeepLinks.mapUrlsForCoordinates(x, y, serverNumber),
        ) { deepLinkLaunched ->
            val messageRes = if (deepLinkLaunched) {
                R.string.map_coord_opened_game
            } else {
                R.string.map_coord_copied_fallback
            }
            Toast.makeText(
                context.applicationContext,
                context.getString(messageRes),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun openFromMessage(context: Context, text: String) {
        val coord = MapCoordinateParser.parse(text) ?: return
        open(context, coord.x, coord.y)
    }
}
