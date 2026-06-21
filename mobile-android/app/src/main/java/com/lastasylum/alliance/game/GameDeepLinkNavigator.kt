package com.lastasylum.alliance.game

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
 * Overlay path: clipboard → delay → direct start (FGS/BAL) → transparent bridge fallback.
 */
object GameDeepLinkNavigator {
    private const val TAG = "GameDeepLinkNavigator"
    internal const val CLIPBOARD_SETTLE_MS = 700L
    private const val GAME_ACTIVITY = "com.games37.sdk.AtlasPluginDemoActivity"

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Start [intent] from FGS / app context on Android 14+ (BAL). */
    internal fun startActivityAllowBackground(context: Context, intent: Intent): Boolean =
        runCatching {
            val options = backgroundActivityStartOptions(context)
            if (options != null) {
                context.startActivity(intent, options)
            } else {
                context.startActivity(intent)
            }
            true
        }.getOrDefault(false)

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

    fun copyToClipboard(context: Context, clipLabel: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
    }

    /** Returns true if any resolved activity accepted the intent. */
    fun openUri(context: Context, uri: String): Boolean {
        if (openDeepLinksToGame(context, listOf(uri))) return true
        if (needsTrampoline(context)) {
            return OverlayGameBridgeActivity.launch(
                context = context.applicationContext,
                clipLabel = "",
                clipText = "",
                uris = listOf(uri),
            )
        }
        return false
    }

    fun openFirstMatching(context: Context, uris: Iterable<String>): Boolean {
        if (openDeepLinksToGame(context, uris)) return true
        if (needsTrampoline(context)) {
            return OverlayGameBridgeActivity.launch(
                context = context.applicationContext,
                clipLabel = "",
                clipText = "",
                uris = uris,
            )
        }
        return false
    }

    /** Fire the first deep link the game accepts (package VIEW → explicit activity). */
    fun openDeepLinksToGame(context: Context, uris: Iterable<String>): Boolean {
        for (launchContext in deepLinkLaunchContexts(context)) {
            for (uri in uris) {
                if (startDeepLinkUri(launchContext, uri)) return true
            }
        }
        return false
    }

    /** Called from [OverlayGameBridgeActivity] after clipboard is set. */
    fun openFirstMatchingFromActivity(activity: Activity, uris: Iterable<String>): Boolean =
        openDeepLinksToGame(activity, uris)

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
        scheduleClipboardDeepLink(
            context = context,
            clipLabel = clipLabel,
            clipText = clipText,
            uris = uris,
            onLaunched = onLaunched,
            allowBridgeFallback = true,
        )
    }

    private fun scheduleClipboardDeepLink(
        context: Context,
        clipLabel: String,
        clipText: String,
        uris: Iterable<String>,
        onLaunched: ((Boolean) -> Unit)? = null,
        allowBridgeFallback: Boolean,
    ) {
        val appContext = context.applicationContext
        val overlayActive = CombatOverlayService.isOverlayServiceRunning()
        if (clipText.isNotBlank()) {
            copyToClipboard(appContext, clipLabel, clipText)
        }
        val delayMs = if (clipText.isNotBlank()) CLIPBOARD_SETTLE_MS else 0L
        mainHandler.postDelayed({
            // Re-copy right before the game reads flyWorldLua clip (overlay must not start our Activity).
            if (clipText.isNotBlank()) {
                copyToClipboard(appContext, clipLabel, clipText)
            }
            if (openDeepLinksToGame(context, uris)) {
                logDebug("map nav deep link ok overlay=$overlayActive clip=$clipText uri=${uris.firstOrNull()}")
                onLaunched?.invoke(true)
                return@postDelayed
            }
            // Bridge steals focus from the game (status/nav bars flash) — never use while overlay is up.
            if (allowBridgeFallback && needsTrampoline(context) && !overlayActive) {
                val bridgeStarted = OverlayGameBridgeActivity.launch(
                    context = appContext,
                    clipLabel = clipLabel,
                    clipText = clipText,
                    uris = uris,
                )
                onLaunched?.invoke(bridgeStarted)
                return@postDelayed
            }
            logFailure(
                "all deep link attempts failed overlay=$overlayActive uris=${uris.take(3)} clip=$clipText",
            )
            onLaunched?.invoke(false)
        }, delayMs)
    }

    private fun deepLinkLaunchContexts(preferred: Context): List<Context> {
        val ordered = mutableListOf<Context>()
        if (CombatOverlayService.isOverlayServiceRunning()) {
            CombatOverlayService.overlayServiceForDeepLinks()?.let { ordered.add(it) }
        }
        if (preferred is Activity) {
            ordered.add(preferred)
        }
        if (preferred !in ordered) {
            ordered.add(preferred)
        }
        val app = preferred.applicationContext
        if (app !in ordered) {
            ordered.add(app)
        }
        return ordered
    }

    private fun startDeepLinkUri(context: Context, uri: String): Boolean {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        for (pkg in targetPackages(context)) {
            if (tryStartActivity(context, buildPackageViewIntent(parsed, pkg), uri)) return true
            if (tryStartActivity(context, buildGameViewIntent(parsed, pkg), uri)) return true
        }
        val generic = Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(deepLinkActivityFlags())
        }
        return tryStartActivity(context, generic, uri)
    }

    private fun tryStartActivity(context: Context, intent: Intent, uriForLog: String): Boolean {
        if (context !is Activity) {
            if (tryStartViaPendingIntent(context, intent, uriForLog)) return true
        }
        return runCatching {
            val options = backgroundActivityStartOptions(context)
            if (options != null) {
                context.startActivity(intent, options)
            } else {
                context.startActivity(intent)
            }
            logDebug("started deep link: $uriForLog via ${context.javaClass.simpleName}")
            true
        }.getOrElse { error ->
            logFailure("startActivity failed: $uriForLog via ${context.javaClass.simpleName} (${error.message})")
            false
        }
    }

    /** Prefer PendingIntent from FGS — avoids launching SquadRelay Activity (BAL-safe on API 34+). */
    private fun tryStartViaPendingIntent(context: Context, intent: Intent, uriForLog: String): Boolean =
        runCatching {
            val pi = PendingIntent.getActivity(
                context.applicationContext,
                uriForLog.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val options = backgroundActivityStartOptions(context)
            if (options != null) {
                pi.send(context, 0, null, null, null, null, options)
            } else {
                pi.send()
            }
            logDebug("started deep link via PendingIntent: $uriForLog")
            true
        }.getOrElse { error ->
            logFailure("PendingIntent.send failed: $uriForLog (${error.message})")
            false
        }

    /** Allow FGS / overlay tap to start the game activity on Android 14+. */
    private fun backgroundActivityStartOptions(context: Context): Bundle? {
        if (context is Activity) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return ActivityOptions.makeBasic().apply {
            setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
        }.toBundle()
    }

    private fun needsTrampoline(context: Context): Boolean = context !is Activity

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

    private fun logFailure(message: String) {
        Log.w(TAG, message)
    }
}
