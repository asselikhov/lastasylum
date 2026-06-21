package com.lastasylum.alliance.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.GameForegroundGate

/**
 * Brings the target game to foreground and fires [globalphslink] VIEW intents.
 * Map coordinates also use clipboard via [GameMapNavigator].
 *
 * Game Lua reads clipboard (`X:{x} Y:{y}` / nick) via [flyWorldLua] after [InvokeDeepLinkActivated].
 */
object GameDeepLinkNavigator {
    internal const val CLIPBOARD_SETTLE_MS = 250L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun targetPackages(context: Context): List<String> =
        AppContainer.from(context).userSettingsPreferences.getOverlayTargetGamePackages()
            .ifEmpty {
                listOf(
                    GameForegroundGate.DEFAULT_TARGET_GAME_PACKAGES_CSV
                        .split(",")
                        .first()
                        .trim(),
                )
            }

    fun bringGameToForeground(context: Context) {
        val appContext = context.applicationContext
        for (pkg in targetPackages(appContext)) {
            val launch = appContext.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            runCatching { appContext.startActivity(launch) }
            return
        }
    }

    fun copyToClipboard(context: Context, clipLabel: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
    }

    /** Returns true if any resolved activity accepted the intent. */
    fun openUri(context: Context, uri: String): Boolean {
        val appContext = context.applicationContext
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        for (pkg in targetPackages(appContext)) {
            val targeted = Intent(Intent.ACTION_VIEW, parsed).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                setPackage(pkg)
            }
            if (appContext.packageManager.resolveActivity(targeted, 0) != null) {
                runCatching {
                    appContext.startActivity(targeted)
                    return true
                }
            }
        }
        val generic = Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (appContext.packageManager.resolveActivity(generic, 0) != null) {
            runCatching {
                appContext.startActivity(generic)
                return true
            }
        }
        return false
    }

    fun openFirstMatching(context: Context, uris: Iterable<String>): Boolean {
        for (uri in uris) {
            if (openUri(context, uri)) return true
        }
        return false
    }

    /**
     * Foreground game → clipboard → short delay → deep link (flyWorldLua reads clip).
     */
    fun openWithClipboard(
        context: Context,
        clipLabel: String,
        clipText: String,
        uris: Iterable<String>,
        onLaunched: ((Boolean) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        bringGameToForeground(appContext)
        copyToClipboard(appContext, clipLabel, clipText)
        mainHandler.postDelayed({
            val launched = openFirstMatching(appContext, uris)
            onLaunched?.invoke(launched)
        }, CLIPBOARD_SETTLE_MS)
    }
}
