package com.lastasylum.alliance.game

import android.content.Context
import android.content.Intent
import android.util.Log
import com.lastasylum.alliance.BuildConfig

/**
 * Closes the in-game «Выбор канала» share dialog after SquadRelay sends to Raid chat.
 * Delivery: broadcast → [MapFlyReceiver] → squadrelay_share_close.json → Frida bridge Lua.
 */
object GameShareCloseBridge {
    private const val TAG = "GameShareCloseBridge"
    private const val EXTRA_SHARE_CLOSE = "shareclose"

    fun close(context: Context): Boolean {
        val appContext = context.applicationContext
        var sent = false
        for (pkg in GameDeepLinkNavigator.targetPackages(appContext)) {
            val trimmed = pkg.trim()
            if (trimmed.isEmpty() || !isInstalled(appContext, trimmed)) continue
            val status = GameMapPatchStatus.read(appContext, listOf(trimmed))
            if (status.state != GameMapPatchStatus.State.PATCH_READY) continue
            val intent = Intent(GameMapFlyBridge.ACTION_MAP_FLY).apply {
                setPackage(trimmed)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(EXTRA_SHARE_CLOSE, true)
            }
            runCatching {
                appContext.sendBroadcast(intent)
                sent = true
                if (BuildConfig.DEBUG) Log.d(TAG, "share close broadcast pkg=$trimmed")
            }.onFailure { e -> Log.w(TAG, "share close broadcast failed pkg=$trimmed", e) }
        }
        return sent
    }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
}
