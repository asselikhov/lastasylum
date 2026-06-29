package com.lastasylum.alliance.game

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lastasylum.alliance.BuildConfig
import org.json.JSONObject
import java.io.File

/**
 * Открытие личного чата с игроком в игре: sdcard-триггер + broadcast в пропатченную игру
 * (Frida-мост опрашивает оба канала и вызывает in-game открытие приватного чата по playerId).
 */
object GamePrivateChatBridge {
    // Доставка через тот же broadcast-action, что и map-fly/autoassault: приёмник в игре
    // пишет приватный файл squadrelay_open_chat.json, который опрашивает Frida-мост.
    private const val EXTRA_OPEN_CHAT = "openchat"
    private const val EXTRA_OC_PLAYER_ID = "ocPlayerId"
    private const val EXTRA_OC_PLAYER_NAME = "ocPlayerName"

    private const val TAG = "GamePrivateChatBridge"
    private const val SDCARD_TRIGGER = "/sdcard/Download/squadrelay_open_chat.json"

    /** Дать оверлею скрыться и игре выйти на передний план до in-process вызова. */
    private const val BRIDGE_OPEN_DELAY_MS = 750L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun send(context: Context, playerId: String, playerName: String): Boolean {
        val appContext = context.applicationContext
        val id = playerId.trim()
        if (id.isEmpty()) return false
        GameDeepLinkNavigator.prepareBridgeFly(appContext)
        mainHandler.postDelayed({
            val sent = dispatch(appContext, id, playerName.trim())
            if (sent) {
                GameDeepLinkNavigator.finishBridgeFly()
            }
        }, BRIDGE_OPEN_DELAY_MS)
        return true
    }

    private fun dispatch(appContext: Context, playerId: String, playerName: String): Boolean {
        var broadcastSent = false
        for (pkg in GameDeepLinkNavigator.targetPackages(appContext)) {
            val trimmed = pkg.trim()
            if (trimmed.isEmpty() || !isInstalled(appContext, trimmed)) continue
            val bridgeStatus = GameMapPatchStatus.read(appContext, listOf(trimmed))
            if (bridgeStatus.state != GameMapPatchStatus.State.PATCH_READY) {
                logDebug("skip pkg=$trimmed bridge=${bridgeStatus.state}")
                continue
            }
            writeSdcardTrigger(playerId, playerName)
            val intent = Intent(GameMapFlyBridge.ACTION_MAP_FLY).apply {
                setPackage(trimmed)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(EXTRA_OPEN_CHAT, true)
                putExtra(EXTRA_OC_PLAYER_ID, playerId)
                putExtra(EXTRA_OC_PLAYER_NAME, playerName)
            }
            try {
                appContext.sendBroadcast(intent)
                broadcastSent = true
                logDebug("open-chat broadcast id=$playerId pkg=$trimmed")
            } catch (e: Exception) {
                Log.w(TAG, "open-chat broadcast failed pkg=$trimmed", e)
            }
        }
        return broadcastSent
    }

    private fun writeSdcardTrigger(playerId: String, playerName: String): Boolean =
        runCatching {
            val json = JSONObject().apply {
                put("playerId", playerId)
                put("playerName", playerName)
                put("ts", System.currentTimeMillis())
            }
            File(SDCARD_TRIGGER).writeText(json.toString())
            logDebug("sdcard trigger $json")
            true
        }.getOrElse { e ->
            Log.w(TAG, "sdcard trigger write failed", e)
            false
        }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
