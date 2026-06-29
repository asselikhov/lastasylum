package com.lastasylum.alliance.game

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R

/**
 * Открывает игру и личный чат с указанным игроком через in-process мост.
 */
object GameChatNavigator {
    private const val TAG = "GameChatNavigator"

    fun openPrivateChat(context: Context, playerId: String, playerName: String) {
        val id = playerId.trim()
        if (id.isEmpty()) {
            logDebug("openPrivateChat: empty playerId")
            return
        }
        val patchReady = GameMapPatchStatus.read(
            context,
            GameDeepLinkNavigator.targetPackages(context),
        ).isAutoFlyAvailable
        if (!patchReady) {
            logDebug("patch not ready; cannot open in-game chat")
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.map_coord_fly_failed),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        GamePrivateChatBridge.send(context, id, playerName)
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
