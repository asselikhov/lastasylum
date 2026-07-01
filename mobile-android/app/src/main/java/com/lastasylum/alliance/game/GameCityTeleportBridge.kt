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
 * Телепорт территории (города) в игре: sdcard-триггер + broadcast в пропатченную игру.
 * Frida-мост: прямое — CityRelocationItem + CityRelocationHandler; альянс — RequestRallyPointRelocateC2S; случайное — ShowNornalPanel + OnOkClickHandler (type=0).
 */
object GameCityTeleportBridge {
  private const val EXTRA_CITY_RELOCATE = "cityrelocate"
  private const val EXTRA_CR_MODE = "crMode"
  private const val EXTRA_CR_X = "crX"
  private const val EXTRA_CR_Y = "crY"
  private const val EXTRA_CR_SID = "crSid"

  private const val TAG = "GameCityTeleportBridge"
  private const val SDCARD_TRIGGER = "/sdcard/Download/squadrelay_city_relocate.json"
  private const val MODE_DIRECT = "direct"
  private const val MODE_ALLIANCE = "alliance"
  private const val MODE_RANDOM = "random"
  private const val RELOCATE_ITEMS_PULSE_SDCARD = "/sdcard/Download/squadrelay_relocate_items_pulse.json"

  private const val BRIDGE_DELAY_MS = 750L

  private val mainHandler = Handler(Looper.getMainLooper())

  fun sendDirect(context: Context, x: Int, y: Int, serverNumber: Int): Boolean {
    if (x <= 0 || y <= 0 || serverNumber <= 0) return false
    return dispatch(context, MODE_DIRECT, x, y, serverNumber)
  }

  fun sendAlliance(context: Context): Boolean =
    dispatch(context, MODE_ALLIANCE, -1, -1, -1)

  fun sendRandom(context: Context): Boolean {
    logInfo("sendRandom: dispatching mode=$MODE_RANDOM")
    return dispatch(context, MODE_RANDOM, -1, -1, -1)
  }

  /** Просит игровой мост немедленно перечитать инвентарь предметов перемещения. */
  fun requestRelocateItemsRefresh(context: Context) {
    runCatching {
      val json = JSONObject().apply {
        put("ts", System.currentTimeMillis())
      }
      File(RELOCATE_ITEMS_PULSE_SDCARD).writeText(json.toString())
      logDebug("relocate-items pulse $json")
    }.onFailure { e ->
      Log.w(TAG, "relocate-items pulse write failed", e)
    }
  }

  private fun dispatch(
    context: Context,
    mode: String,
    x: Int,
    y: Int,
    serverNumber: Int,
  ): Boolean {
    val appContext = context.applicationContext
    GameDeepLinkNavigator.prepareBridgeFly(appContext)
    mainHandler.postDelayed({
      val sent = sendBroadcast(appContext, mode, x, y, serverNumber)
      if (sent) {
        GameDeepLinkNavigator.finishBridgeFly()
      }
    }, BRIDGE_DELAY_MS)
    return true
  }

  private fun sendBroadcast(
    appContext: Context,
    mode: String,
    x: Int,
    y: Int,
    serverNumber: Int,
  ): Boolean {
    var broadcastSent = false
    for (pkg in GameDeepLinkNavigator.targetPackages(appContext)) {
      val trimmed = pkg.trim()
      if (trimmed.isEmpty() || !isInstalled(appContext, trimmed)) continue
      val bridgeStatus = GameMapPatchStatus.read(appContext, listOf(trimmed))
      if (bridgeStatus.state != GameMapPatchStatus.State.PATCH_READY) {
        logDebug("skip pkg=$trimmed bridge=${bridgeStatus.state}")
        continue
      }
      writeSdcardTrigger(mode, x, y, serverNumber)
      val intent = Intent(GameMapFlyBridge.ACTION_MAP_FLY).apply {
        setPackage(trimmed)
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        putExtra(EXTRA_CITY_RELOCATE, true)
        putExtra(EXTRA_CR_MODE, mode)
        if (mode == MODE_DIRECT) {
          putExtra(EXTRA_CR_X, x)
          putExtra(EXTRA_CR_Y, y)
          putExtra(EXTRA_CR_SID, serverNumber)
        }
      }
      try {
        appContext.sendBroadcast(intent)
        broadcastSent = true
        logInfo("city-relocate broadcast mode=$mode pkg=$trimmed x=$x y=$y sid=$serverNumber")
        logDebug("city-relocate broadcast mode=$mode pkg=$trimmed")
      } catch (e: Exception) {
        Log.w(TAG, "city-relocate broadcast failed pkg=$trimmed", e)
      }
    }
    return broadcastSent
  }

  private fun writeSdcardTrigger(
    mode: String,
    x: Int,
    y: Int,
    serverNumber: Int,
  ): Boolean =
    runCatching {
      val json = JSONObject().apply {
        put("mode", mode)
        put("ts", System.currentTimeMillis())
        if (mode == MODE_DIRECT) {
          put("x", x)
          put("y", y)
          put("sid", serverNumber)
        }
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

  private fun logInfo(message: String) {
    Log.i(TAG, message)
  }
}
