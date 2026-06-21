package com.lastasylum.alliance.game

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.GameForegroundGate
import com.lastasylum.alliance.overlay.OverlayGameBridgeActivity

/**
 * Brings the target game to foreground and fires [globalphslink] VIEW intents.
 * Map coordinates also use clipboard via [GameMapNavigator].
 *
 * From overlay [android.app.Service], deep links are delivered directly while FGS is active
 * so [OverlayGameBridgeActivity] does not steal focus (system bars + paused game).
 */
object GameDeepLinkNavigator {
    private const val TAG = "GameDeepLinkNavigator"
    internal const val CLIPBOARD_SETTLE_MS = 700L
    private const val GAME_ACTIVITY = "com.games37.sdk.AtlasPluginDemoActivity"

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
        if (deliverViaOverlayDirect(context, clipLabel = "", clipText = "", uris = listOf(uri))) {
            return true
        }
        if (needsTrampoline(context)) {
            OverlayGameBridgeActivity.launch(
                context = context.applicationContext,
                clipLabel = "",
                clipText = "",
                uris = listOf(uri),
            )
            return true
        }
        return openUriDirect(context.applicationContext, uri)
    }

    fun openFirstMatching(context: Context, uris: Iterable<String>): Boolean {
        if (deliverViaOverlayDirect(context, clipLabel = "", clipText = "", uris = uris)) {
            return true
        }
        if (needsTrampoline(context)) {
            OverlayGameBridgeActivity.launch(
                context = context.applicationContext,
                clipLabel = "",
                clipText = "",
                uris = uris,
            )
            return true
        }
        return openDeepLinksToGame(context.applicationContext, uris)
    }

    /** Fire the first resolvable deep link to the configured game package(s). */
    fun openDeepLinksToGame(context: Context, uris: Iterable<String>): Boolean {
        for (uri in uris) {
            if (openUriDirect(context.applicationContext, uri)) return true
        }
        return false
    }

    /** Called from [OverlayGameBridgeActivity] after clipboard is set. */
    fun openFirstMatchingFromActivity(activity: Activity, uris: Iterable<String>): Boolean =
        openDeepLinksToGame(activity.applicationContext, uris)

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
        if (deliverViaOverlayDirect(context, clipLabel, clipText, uris, onLaunched)) {
            return
        }
        if (needsTrampoline(context)) {
            OverlayGameBridgeActivity.launch(
                context = context.applicationContext,
                clipLabel = clipLabel,
                clipText = clipText,
                uris = uris,
            )
            return
        }
        scheduleClipboardDeepLink(
            context = context.applicationContext,
            clipLabel = clipLabel,
            clipText = clipText,
            uris = uris,
            onLaunched = onLaunched,
        )
    }

    /**
     * While overlay FGS is active, copy + deep link without [OverlayGameBridgeActivity].
     * Starting our Activity pauses the game and shows system nav/status bars.
     */
    private fun deliverViaOverlayDirect(
        context: Context,
        clipLabel: String,
        clipText: String,
        uris: Iterable<String>,
        onLaunched: ((Boolean) -> Unit)? = null,
    ): Boolean {
        if (!needsTrampoline(context) || !CombatOverlayService.isOverlayServiceRunning()) {
            return false
        }
        scheduleClipboardDeepLink(
            context = context.applicationContext,
            clipLabel = clipLabel,
            clipText = clipText,
            uris = uris,
            onLaunched = onLaunched,
        )
        return true
    }

    private fun scheduleClipboardDeepLink(
        context: Context,
        clipLabel: String,
        clipText: String,
        uris: Iterable<String>,
        onLaunched: ((Boolean) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        if (clipText.isNotBlank()) {
            copyToClipboard(appContext, clipLabel, clipText)
        }
        val delayMs = if (clipText.isNotBlank()) CLIPBOARD_SETTLE_MS else 0L
        mainHandler.postDelayed({
            val launched = openDeepLinksToGame(appContext, uris)
            onLaunched?.invoke(launched)
        }, delayMs)
    }

    private fun needsTrampoline(context: Context): Boolean = context !is Activity

    private fun openUriDirect(context: Context, uri: String): Boolean {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        for (pkg in targetPackages(context)) {
            val packageIntent = buildPackageViewIntent(parsed, pkg)
            if (context.packageManager.resolveActivity(packageIntent, 0) != null) {
                runCatching {
                    context.startActivity(packageIntent)
                    logDebug("opened via package intent: $uri -> $pkg")
                    return true
                }.onFailure { e ->
                    logDebug("package intent failed: $uri -> $pkg (${e.message})")
                }
            }
            val targeted = buildGameViewIntent(parsed, pkg)
            if (context.packageManager.resolveActivity(targeted, 0) != null) {
                runCatching {
                    context.startActivity(targeted)
                    logDebug("opened via explicit activity: $uri -> $pkg")
                    return true
                }.onFailure { e ->
                    logDebug("explicit activity failed: $uri -> $pkg (${e.message})")
                }
            }
        }
        val generic = Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(deepLinkActivityFlags())
        }
        if (context.packageManager.resolveActivity(generic, 0) != null) {
            runCatching {
                context.startActivity(generic)
                logDebug("opened via generic VIEW: $uri")
                return true
            }.onFailure { e ->
                logDebug("generic VIEW failed: $uri (${e.message})")
            }
        }
        logDebug("no handler for deep link: $uri")
        return false
    }

    private fun buildPackageViewIntent(parsed: Uri, pkg: String): Intent =
        Intent(Intent.ACTION_VIEW, parsed).apply {
            setPackage(pkg)
            addFlags(deepLinkActivityFlags())
        }

    private fun buildGameViewIntent(parsed: Uri, pkg: String): Intent =
        Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(deepLinkActivityFlags())
            setClassName(pkg, GAME_ACTIVITY)
        }

    private fun deepLinkActivityFlags(): Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
