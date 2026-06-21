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
 *
 * Map fly (flyWorldLua): clip `[#:server X:x Y:y]` / `X:x Y:y` + path deep link.
 * External apps cannot rely on the game reading cross-app clipboard alone — attach
 * [ClipData] and [Intent.EXTRA_TEXT] on every VIEW intent as well.
 */
object GameDeepLinkNavigator {
    private const val TAG = "GameDeepLinkNavigator"
    internal const val CLIPBOARD_SETTLE_MS = 900L
    private const val MAP_BURST_STEP_MS = 500L
    private const val GAME_ACTIVITY = "com.games37.sdk.AtlasPluginDemoActivity"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var pendingMapBurstCallback: ((Boolean) -> Unit)? = null

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
        if (text.isBlank()) return
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
    fun openDeepLinksToGame(
        context: Context,
        uris: Iterable<String>,
        clipPayloads: List<String> = emptyList(),
    ): Boolean {
        for (launchContext in deepLinkLaunchContexts(context)) {
            for (uri in uris) {
                if (startDeepLinkUri(launchContext, uri, clipPayloads)) return true
            }
        }
        return false
    }

    /**
     * Map coordinate navigation: clipboard + staggered deep links.
     * When overlay is active, touch is passthrough so the game can read clip / handle intent.
     */
    fun openMapCoordinates(
        context: Context,
        clipLabel: String,
        clipText: String,
        bracketClipText: String,
        x: Int,
        y: Int,
        serverNumber: Int?,
        onLaunched: ((Boolean) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        val clipPayloads = clipPayloads(bracketClipText, clipText)
        copyClipPayloads(appContext, clipLabel, clipPayloads)
        val burstUris = GameSearchDeepLinks.mapFlyBurstUrls(x, y, serverNumber)
        logInfo(
            "map burst scheduled clips=$clipPayloads uris=$burstUris " +
                "overlay=${CombatOverlayService.isOverlayServiceRunning()}",
        )

        if (needsTrampoline(context)) {
            pendingMapBurstCallback = onLaunched
            val relayStarted = OverlayGameBridgeActivity.launch(
                context = appContext,
                clipLabel = clipLabel,
                clipText = primaryClipText(bracketClipText, clipText),
                bracketClipText = bracketClipText,
                uris = burstUris,
                mapBurst = true,
            )
            if (relayStarted) {
                scheduleMapFlyBurst(
                    context = appContext,
                    clipLabel = clipLabel,
                    clipPayloads = clipPayloads,
                    uris = burstUris,
                    onLaunched = pendingMapBurstCallback.also { pendingMapBurstCallback = null },
                )
                return
            }
            pendingMapBurstCallback = null
            logFailure("map relay launch failed; running burst from service")
        }

        scheduleMapFlyBurst(
            context = context,
            clipLabel = clipLabel,
            clipPayloads = clipPayloads,
            uris = burstUris,
            onLaunched = onLaunched,
        )
    }

    private fun scheduleMapFlyBurst(
        context: Context,
        clipLabel: String,
        clipPayloads: List<String>,
        uris: List<String>,
        onLaunched: ((Boolean) -> Unit)?,
    ) {
        val appContext = context.applicationContext
        val launchContext = CombatOverlayService.overlayServiceForDeepLinks() ?: context
        var anyLaunched = false
        val steps = uris.size.coerceAtLeast(1)

        CombatOverlayService.prepareForGameDeepLink()

        mainHandler.postDelayed({
            copyClipPayloads(appContext, clipLabel, clipPayloads)
            uris.forEachIndexed { index, uri ->
                mainHandler.postDelayed({
                    copyClipPayloads(appContext, clipLabel, clipPayloads)
                    if (openDeepLinksToGame(launchContext, listOf(uri), clipPayloads)) {
                        anyLaunched = true
                        logInfo("map burst step $index ok uri=$uri")
                    } else {
                        logFailure("map burst step $index failed uri=$uri")
                    }
                }, index * MAP_BURST_STEP_MS)
            }
            mainHandler.postDelayed({
                CombatOverlayService.finishGameDeepLink()
                onLaunched?.invoke(anyLaunched)
            }, MAP_BURST_STEP_MS * steps + 80L)
        }, CLIPBOARD_SETTLE_MS)
    }

    /** Foreground game → clipboard → short delay → deep link. */
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
        val clipPayloads = listOf(clipText).filter { it.isNotBlank() }
        copyClipPayloads(appContext, clipLabel, clipPayloads)
        val delayMs = if (clipText.isNotBlank()) CLIPBOARD_SETTLE_MS else 0L
        mainHandler.postDelayed({
            copyClipPayloads(appContext, clipLabel, clipPayloads)
            if (openDeepLinksToGame(context, uris, clipPayloads)) {
                onLaunched?.invoke(true)
                return@postDelayed
            }
            if (allowBridgeFallback && needsTrampoline(context)) {
                val bridgeStarted = OverlayGameBridgeActivity.launch(
                    context = appContext,
                    clipLabel = clipLabel,
                    clipText = clipText,
                    uris = uris,
                    mapBurst = false,
                )
                onLaunched?.invoke(bridgeStarted)
                return@postDelayed
            }
            logFailure("all deep link attempts failed uris=${uris.take(3)} clip=$clipText")
            onLaunched?.invoke(false)
        }, delayMs)
    }

    private fun clipPayloads(bracketClipText: String, xyClipText: String): List<String> =
        buildList {
            if (bracketClipText.isNotBlank()) add(bracketClipText)
            if (xyClipText.isNotBlank() && xyClipText != bracketClipText) add(xyClipText)
        }

    private fun primaryClipText(bracketClipText: String, xyClipText: String): String =
        bracketClipText.ifBlank { xyClipText }

    private fun copyClipPayloads(context: Context, clipLabel: String, payloads: List<String>) {
        val primary = payloads.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
        copyToClipboard(context, clipLabel, primary)
    }

    private fun deepLinkLaunchContexts(preferred: Context): List<Context> {
        val ordered = mutableListOf<Context>()
        if (preferred is Activity) {
            ordered.add(preferred)
        }
        if (CombatOverlayService.isOverlayServiceRunning()) {
            CombatOverlayService.overlayServiceForDeepLinks()?.let { ordered.add(it) }
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

    private fun startDeepLinkUri(
        context: Context,
        uri: String,
        clipPayloads: List<String>,
    ): Boolean {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        for (pkg in targetPackages(context)) {
            if (tryStartActivity(context, buildPackageViewIntent(parsed, pkg, clipPayloads), uri)) {
                return true
            }
            if (tryStartActivity(context, buildGameViewIntent(parsed, pkg, clipPayloads), uri)) {
                return true
            }
        }
        val generic = buildViewIntent(parsed, clipPayloads)
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
            logFailure(
                "startActivity failed: $uriForLog via ${context.javaClass.simpleName} (${error.message})",
            )
            false
        }
    }

    /** Prefer PendingIntent from FGS — BAL-safe on API 34+. */
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

    private fun buildPackageViewIntent(parsed: Uri, pkg: String, clipPayloads: List<String>): Intent =
        buildViewIntent(parsed, clipPayloads).apply { setPackage(pkg) }

    private fun buildGameViewIntent(parsed: Uri, pkg: String, clipPayloads: List<String>): Intent =
        buildViewIntent(parsed, clipPayloads).apply { setClassName(pkg, GAME_ACTIVITY) }

    private fun buildViewIntent(parsed: Uri, clipPayloads: List<String>): Intent =
        Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(deepLinkActivityFlags())
            attachClipPayloads(this, clipPayloads)
        }

    private fun attachClipPayloads(intent: Intent, clipPayloads: List<String>) {
        val text = clipPayloads.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
        intent.clipData = ClipData.newPlainText("map_coordinates", text)
        intent.putExtra(Intent.EXTRA_TEXT, text)
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

    private fun logInfo(message: String) {
        Log.i(TAG, message)
    }

    private fun logFailure(message: String) {
        Log.w(TAG, message)
    }
}
