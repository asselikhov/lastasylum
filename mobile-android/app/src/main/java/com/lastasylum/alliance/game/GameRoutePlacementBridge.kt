package com.lastasylum.alliance.game

import android.content.Context
import android.content.Intent
import android.util.Log
import com.lastasylum.alliance.BuildConfig
import org.json.JSONObject
import java.io.File

/**
 * Режим прокладки точки маршрута на карте: 3×3 без расхода предмета перемещения.
 */
object GameRoutePlacementBridge {
    private const val TAG = "GameRoutePlacementBridge"
    private const val EXTRA_ROUTE_PLACEMENT = "routePlacement"
    private const val EXTRA_RP_MODE = "rpMode"
    private const val EXTRA_RP_X = "rpX"
    private const val EXTRA_RP_Y = "rpY"
    private const val EXTRA_RP_SID = "rpSid"

    private const val SDCARD_TRIGGER = "/sdcard/Download/squadrelay_route_placement.json"

    const val MODE_START = "start"
    const val MODE_CANCEL = "cancel"
    const val MODE_CONFIRM = "confirm"

    fun start(context: Context, x: Int, y: Int, sid: Int): Boolean =
        dispatch(context, MODE_START, x, y, sid)

    fun cancel(context: Context): Boolean =
        dispatch(context, MODE_CANCEL, -1, -1, -1)

    fun confirm(context: Context): Boolean =
        dispatch(context, MODE_CONFIRM, -1, -1, -1)

    private fun dispatch(
        context: Context,
        mode: String,
        x: Int,
        y: Int,
        sid: Int,
    ): Boolean {
        val appContext = context.applicationContext
        GameDeepLinkNavigator.prepareBridgeFly(appContext)
        val sent = sendBroadcast(appContext, mode, x, y, sid)
        if (sent) {
            GameDeepLinkNavigator.finishBridgeFly()
        }
        return sent
    }

    private fun sendBroadcast(
        appContext: Context,
        mode: String,
        x: Int,
        y: Int,
        sid: Int,
    ): Boolean {
        var broadcastSent = false
        writeSdcardTrigger(mode, x, y, sid)
        for (pkg in GameDeepLinkNavigator.targetPackages(appContext)) {
            val trimmed = pkg.trim()
            if (trimmed.isEmpty()) continue
            val bridgeStatus = GameMapPatchStatus.read(appContext, listOf(trimmed))
            if (bridgeStatus.state != GameMapPatchStatus.State.PATCH_READY) continue
            val intent = Intent(GameMapFlyBridge.ACTION_MAP_FLY).apply {
                setPackage(trimmed)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(EXTRA_ROUTE_PLACEMENT, true)
                putExtra(EXTRA_RP_MODE, mode)
                if (mode == MODE_START) {
                    putExtra(EXTRA_RP_X, x)
                    putExtra(EXTRA_RP_Y, y)
                    putExtra(EXTRA_RP_SID, sid)
                }
            }
            try {
                appContext.sendBroadcast(intent)
                broadcastSent = true
                logInfo("route-placement broadcast mode=$mode pkg=$trimmed")
            } catch (e: Exception) {
                Log.w(TAG, "route-placement broadcast failed pkg=$trimmed", e)
            }
        }
        return broadcastSent
    }

    private fun writeSdcardTrigger(mode: String, x: Int, y: Int, sid: Int): Boolean =
        runCatching {
            val json = JSONObject().apply {
                put("mode", mode)
                put("ts", System.currentTimeMillis())
                if (mode == MODE_START) {
                    put("x", x)
                    put("y", y)
                    put("sid", sid)
                }
            }
            File(SDCARD_TRIGGER).writeText(json.toString())
            true
        }.getOrElse { e ->
            Log.w(TAG, "sdcard trigger write failed", e)
            false
        }

    private fun logInfo(message: String) {
        Log.i(TAG, message)
    }
}

/** Результат подтверждения точки маршрута из игры. */
data class RoutePlacementResult(
    val ok: Boolean,
    val x: Int,
    val y: Int,
    val sid: Int,
    val error: String?,
) {
    companion object {
        fun fromJson(json: String?): RoutePlacementResult? {
            val raw = json?.trim().orEmpty()
            if (raw.isEmpty()) return null
            return runCatching {
                val o = JSONObject(raw)
                RoutePlacementResult(
                    ok = o.optBoolean("ok", false),
                    x = o.optInt("x", 0),
                    y = o.optInt("y", 0),
                    sid = o.optInt("sid", 0),
                    error = o.optString("error").trim().takeIf { it.isNotEmpty() },
                )
            }.getOrNull()
        }
    }
}
