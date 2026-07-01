package com.lastasylum.alliance.game

import android.content.Context
import android.content.Intent
import android.util.Log
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.di.AppContainer
import org.json.JSONObject
import java.io.File

/**
 * Map fly via sdcard trigger file + broadcast to patched game (Frida bridge polls both).
 */
object GameMapFlyBridge {
    const val ACTION_MAP_FLY = "com.lastasylum.alliance.action.MAP_FLY"

    const val EXTRA_X = "x"
    const val EXTRA_Y = "y"
    const val EXTRA_SERVER = "server"
    const val EXTRA_CROSS_SERVER = "crossServer"

    private const val TAG = "GameMapFlyBridge"
    private const val MAP_FLY_RECEIVER = "com.lastasylum.alliance.game.MapFlyReceiver"
    private const val SDCARD_TRIGGER = "/sdcard/Download/squadrelay_map_fly.json"

    fun send(
        context: Context,
        x: Int,
        y: Int,
        serverNumber: Int?,
        crossServer: Boolean = false,
    ): Boolean {
        val appContext = context.applicationContext
        val resolvedServer = serverNumber ?: resolveActiveServerNumber(appContext)
        val activeServer = resolveActiveServerNumber(appContext)
        val cross = crossServer ||
            (resolvedServer != null && activeServer != null && resolvedServer != activeServer)
        GameDeepLinkNavigator.prepareBridgeFly(appContext)
        val sent = dispatchFlyBroadcast(appContext, x, y, resolvedServer, cross)
        if (sent) {
            GameDeepLinkNavigator.finishBridgeFly()
        }
        return sent
    }

    private fun dispatchFlyBroadcast(
        appContext: Context,
        x: Int,
        y: Int,
        resolvedServer: Int?,
        cross: Boolean,
    ): Boolean {
        var broadcastSent = false
        for (pkg in GameDeepLinkNavigator.targetPackages(appContext)) {
            val trimmed = pkg.trim()
            if (trimmed.isEmpty() || !isInstalled(appContext, trimmed)) {
                continue
            }
            val bridgeStatus = GameMapPatchStatus.read(appContext, listOf(trimmed))
            if (bridgeStatus.state != GameMapPatchStatus.State.PATCH_READY) {
                logDebug("skip pkg=$trimmed bridge=${bridgeStatus.state}")
                continue
            }
            writeSdcardTrigger(x, y, resolvedServer, cross)
            val broadcastIntent = Intent(ACTION_MAP_FLY).apply {
                setPackage(trimmed)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(EXTRA_X, x)
                putExtra(EXTRA_Y, y)
                if (resolvedServer != null) {
                    putExtra(EXTRA_SERVER, resolvedServer)
                }
                putExtra(EXTRA_CROSS_SERVER, cross)
            }
            try {
                appContext.sendBroadcast(broadcastIntent)
                broadcastSent = true
                logDebug(
                    "fly broadcast x=$x y=$y server=$resolvedServer cross=$cross pkg=$trimmed",
                )
            } catch (e: Exception) {
                Log.w(TAG, "fly broadcast failed pkg=$trimmed", e)
            }
        }
        return broadcastSent
    }

    private fun writeSdcardTrigger(
        x: Int,
        y: Int,
        server: Int?,
        cross: Boolean,
    ): Boolean =
        runCatching {
            val json = JSONObject().apply {
                put("x", x)
                put("y", y)
                put("server", server ?: -1)
                put("crossServer", cross)
            }
            File(SDCARD_TRIGGER).writeText(json.toString())
            logDebug("sdcard trigger $json")
            true
        }.getOrElse { e ->
            Log.w(TAG, "sdcard trigger write failed", e)
            false
        }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
        }.isSuccess

    private fun resolveActiveServerNumber(context: Context): Int? {
        val repo = AppContainer.from(context).usersRepository
        return repo.peekMyProfile()?.activeServerNumber
            ?: repo.peekMyProfileDisk()?.activeServerNumber
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
