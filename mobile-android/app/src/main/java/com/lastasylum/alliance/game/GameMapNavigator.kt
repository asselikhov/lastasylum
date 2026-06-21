package com.lastasylum.alliance.game

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lastasylum.alliance.R

/**
 * Opens the configured target game at map coordinates: foreground, clipboard, delay, deep link.
 */
object GameMapNavigator {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun open(context: Context, x: Int, y: Int, serverNumber: Int? = null) {
        val appContext = context.applicationContext
        GameDeepLinkNavigator.bringGameToForeground(appContext)
        copyCoordinatesToClipboard(appContext, x, y)
        mainHandler.postDelayed({
            val deepLinkLaunched = GameDeepLinkNavigator.openFirstMatching(
                appContext,
                GameSearchDeepLinks.mapUrlsForCoordinates(x, y, serverNumber),
            )
            val messageRes = if (deepLinkLaunched) {
                R.string.map_coord_opened_game
            } else {
                R.string.map_coord_copied_fallback
            }
            Toast.makeText(appContext, appContext.getString(messageRes), Toast.LENGTH_SHORT).show()
        }, GameDeepLinkNavigator.CLIPBOARD_SETTLE_MS)
    }

    fun openFromMessage(context: Context, text: String) {
        val coord = MapCoordinateParser.parse(text) ?: return
        open(context, coord.x, coord.y)
    }

    private fun copyCoordinatesToClipboard(context: Context, x: Int, y: Int) {
        GameDeepLinkNavigator.copyToClipboard(
            context,
            clipLabel = "map_coordinates",
            text = "X:$x Y:$y",
        )
    }
}
