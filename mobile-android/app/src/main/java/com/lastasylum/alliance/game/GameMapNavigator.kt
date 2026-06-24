package com.lastasylum.alliance.game

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService

/**
 * Opens the target game and flies the map to coordinates via in-process bridge broadcast.
 */
object GameMapNavigator {
    private const val TAG = "GameMapNavigator"

    fun open(context: Context, x: Int, y: Int, serverNumber: Int? = null) {
        val overlayActive = CombatOverlayService.isOverlayServiceRunning()
        logDebug(
            "open x=$x y=$y server=$serverNumber overlay=$overlayActive",
        )
        val patchReady = GameMapPatchStatus.read(
            context,
            GameDeepLinkNavigator.targetPackages(context),
        ).isAutoFlyAvailable
        val bridgeSent = if (patchReady) {
            GameMapFlyBridge.send(context, x, y, serverNumber)
        } else {
            logDebug("patch not ready; deep link fallback only")
            false
        }
        if (bridgeSent) {
            // The in-process bridge performs the jump and foregrounds the game itself
            // (GameMapFlyBridge.send -> prepareBridgeFly -> resumeTargetGame). The deep-link
            // burst below is only for the stock build and writes coords to the clipboard, which
            // triggers Android's "pasted from clipboard" popup on every fly — so skip it here.
            logDebug("bridge sent; skip deep link/clipboard burst")
            return
        }
        if (overlayActive) {
            logDebug("patch not ready; deep link fallback")
        }
        GameDeepLinkNavigator.openMapAtCoordinates(
            context = context,
            x = x,
            y = y,
            serverNumber = serverNumber,
        ) { gameForegrounded ->
            if (CombatOverlayService.isOverlayServiceRunning()) {
                if (!gameForegrounded) {
                    toast(context, R.string.map_coord_fly_failed)
                }
                return@openMapAtCoordinates
            }
            if (!gameForegrounded) {
                toast(context, R.string.map_coord_fly_failed)
            }
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
