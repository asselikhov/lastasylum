package com.lastasylum.alliance.game

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService

/**
 * Opens the configured target game at map coordinates: clipboard, delay, deep link.
 */
object GameMapNavigator {
    private const val TAG = "GameMapNavigator"

    fun open(context: Context, x: Int, y: Int, serverNumber: Int? = null) {
        val coord = MapCoordinate(null, x, y, serverNumber)
        val clipText = coord.mapClipboardText()
        val bracketClipText = coord.gameBracketText()
        logDebug(
            "open x=$x y=$y server=$serverNumber clip=$clipText bracket=$bracketClipText " +
                "overlay=${CombatOverlayService.isOverlayServiceRunning()}",
        )
        GameDeepLinkNavigator.openMapCoordinates(
            context = context,
            clipLabel = "map_coordinates",
            clipText = clipText,
            bracketClipText = bracketClipText,
            x = x,
            y = y,
            serverNumber = serverNumber,
        ) { deepLinkLaunched ->
            if (CombatOverlayService.isOverlayServiceRunning()) {
                if (!deepLinkLaunched) {
                    toast(context, R.string.map_coord_copied_fallback)
                }
                return@openMapCoordinates
            }
            val messageRes = if (deepLinkLaunched) {
                R.string.map_coord_opened_game
            } else {
                R.string.map_coord_copied_fallback
            }
            toast(context, messageRes)
        }
    }

    fun openFromMessage(context: Context, text: String, serverNumber: Int? = null) {
        val coord = MapCoordinateParser.parse(text.trim()) ?: run {
            logDebug("openFromMessage: no coordinates in '$text'")
            return
        }
        val server = coord.serverNumber ?: serverNumber ?: resolveActiveServerNumber(context)
        open(context, coord.x, coord.y, server)
    }

    private fun resolveActiveServerNumber(context: Context): Int? {
        val repo = AppContainer.from(context.applicationContext).usersRepository
        return repo.peekMyProfile()?.activeServerNumber
            ?: repo.peekMyProfileDisk()?.activeServerNumber
    }

    private fun toast(context: Context, resId: Int) {
        Toast.makeText(
            context.applicationContext,
            context.getString(resId),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
