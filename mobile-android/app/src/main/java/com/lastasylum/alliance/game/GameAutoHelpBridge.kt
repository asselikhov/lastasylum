package com.lastasylum.alliance.game

import android.content.Context
import android.content.Intent
import android.util.Log
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.settings.UserSettingsPreferences

/**
 * Auto-help bridge: broadcasts the auto-help config to the patched game's in-process Frida
 * bridge (via [MapFlyReceiver], reusing the MAP_FLY action). The receiver writes the config
 * to the game's private files dir, which the bridge polls. While enabled, the bridge
 * periodically calls the alliance "help all" network action (UnionHelpAllC2S) — the same
 * request the in-game "Помощь" button sends — gated on whether there is anything to help.
 *
 * The game can't read SquadRelay's files under scoped storage, so a broadcast (not a shared
 * file) is the delivery channel.
 */
object GameAutoHelpBridge {
    private const val TAG = "GameAutoHelpBridge"
    private const val EXTRA_AUTO_HELP = "autohelp"
    private const val EXTRA_AH_ENABLED = "ahEnabled"
    private const val EXTRA_AH_INTERVAL = "ahInterval"

    /** Broadcasts the current auto-help settings to the patched game. */
    fun sync(context: Context): Boolean {
        val prefs = UserSettingsPreferences(context.applicationContext)
        return write(context, prefs.isAutoHelpEnabled(), prefs.getAutoHelpIntervalSec())
    }

    fun write(context: Context, enabled: Boolean, intervalSec: Int): Boolean {
        val appContext = context.applicationContext
        val clamped = intervalSec.coerceIn(
            UserSettingsPreferences.AUTO_HELP_INTERVAL_MIN_SEC,
            UserSettingsPreferences.AUTO_HELP_INTERVAL_MAX_SEC,
        )
        var sent = false
        for (pkg in GameDeepLinkNavigator.targetPackages(appContext)) {
            val trimmed = pkg.trim()
            if (trimmed.isEmpty() || !isInstalled(appContext, trimmed)) continue
            val status = GameMapPatchStatus.read(appContext, listOf(trimmed))
            if (status.state != GameMapPatchStatus.State.PATCH_READY) continue
            val intent = Intent(GameMapFlyBridge.ACTION_MAP_FLY).apply {
                setPackage(trimmed)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(EXTRA_AUTO_HELP, true)
                putExtra(EXTRA_AH_ENABLED, enabled)
                putExtra(EXTRA_AH_INTERVAL, clamped)
            }
            runCatching {
                appContext.sendBroadcast(intent)
                sent = true
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "autohelp broadcast enabled=$enabled interval=$clamped pkg=$trimmed")
                }
            }.onFailure { e -> Log.w(TAG, "autohelp broadcast failed pkg=$trimmed", e) }
        }
        return sent
    }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
}
