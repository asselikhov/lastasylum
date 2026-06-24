package com.lastasylum.alliance.game

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
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
 * Map coordinate fly is handled in-process by [GameMapFlyBridge] + Frida bridge;
 * deep links here only raise the game / open the world map shell.
 */
object GameDeepLinkNavigator {
    private const val TAG = "GameDeepLinkNavigator"
    internal const val CLIPBOARD_SETTLE_MS = 1200L
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

    /** Hide overlay and raise the game before in-process map fly. */
    fun prepareBridgeFly(context: Context) {
        CombatOverlayService.prepareForGameDeepLink()
        resumeTargetGame(context)
    }

    fun finishBridgeFly(delayMs: Long = 900L) {
        mainHandler.postDelayed({ CombatOverlayService.finishGameDeepLink() }, delayMs)
    }

    /** Bring patched game activity to foreground (overlay / background safe). */
    fun resumeTargetGame(context: Context): Boolean {
        val appContext = context.applicationContext
        for (pkg in targetPackages(appContext)) {
            val intent = Intent().apply {
                component = ComponentName(pkg, GAME_ACTIVITY)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }
            if (startActivityAllowBackground(appContext, intent)) {
                logInfo("resumed game pkg=$pkg")
                return true
            }
        }
        return false
    }

    /**
     * Bring game to foreground, copy in-game coord format to clipboard, fire map deep links.
     * Auto-fly to exact tile requires patched game + [GameMapFlyBridge]; stock build may only open the map.
     */
    fun openMapAtCoordinates(
        context: Context,
        x: Int,
        y: Int,
        serverNumber: Int?,
        onLaunched: ((Boolean) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        val resolvedServer = serverNumber
            ?: AppContainer.from(appContext).usersRepository.peekMyProfile()?.activeServerNumber
            ?: AppContainer.from(appContext).usersRepository.peekMyProfileDisk()?.activeServerNumber
        val coord = MapCoordinate(null, x, y, resolvedServer)
        val bracketClip = coord.gameBracketText()
        val xyClip = coord.mapClipboardText()
        val uris = GameSearchDeepLinks.mapFlyBurstUrls(x, y, resolvedServer)
        logInfo(
            "map fly scheduled uris=$uris bracket=$bracketClip overlay=${CombatOverlayService.isOverlayServiceRunning()}",
        )

        if (needsTrampoline(context)) {
            pendingMapBurstCallback = onLaunched
            val relayStarted = OverlayGameBridgeActivity.launch(
                context = appContext,
                clipLabel = "map_coord",
                clipText = xyClip,
                uris = uris,
                mapBurst = true,
                bracketClipText = bracketClip,
            )
            if (relayStarted) {
                scheduleMapBurst(
                    context = appContext,
                    bracketClip = bracketClip,
                    xyClip = xyClip,
                    uris = uris,
                    onLaunched = pendingMapBurstCallback.also { pendingMapBurstCallback = null },
                )
                return
            }
            pendingMapBurstCallback = null
            logFailure("map relay launch failed; running burst from service")
        }

        scheduleMapBurst(
            context = context,
            bracketClip = bracketClip,
            xyClip = xyClip,
            uris = uris,
            onLaunched = onLaunched,
        )
    }

    private fun scheduleMapBurst(
        context: Context,
        bracketClip: String,
        xyClip: String,
        uris: List<String>,
        onLaunched: ((Boolean) -> Unit)?,
    ) {
        val launchContext = CombatOverlayService.overlayServiceForDeepLinks() ?: context
        val clipPayloads = clipPayloads(bracketClip, xyClip)
        var launched = false

        CombatOverlayService.prepareForGameDeepLink()
        copyClipPayloads(launchContext.applicationContext, "map_coord", clipPayloads)

        mainHandler.postDelayed({
            copyClipPayloads(launchContext.applicationContext, "map_coord", clipPayloads)
            for (uri in uris) {
                if (openDeepLinksToGame(launchContext, listOf(uri), clipPayloads)) {
                    launched = true
                    logInfo("map burst ok uri=$uri")
                    break
                }
                logFailure("map burst failed uri=$uri")
            }
            mainHandler.postDelayed({
                CombatOverlayService.finishGameDeepLink()
                onLaunched?.invoke(launched)
            }, 120L)
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
            val balStarted = runCatching {
                val options = backgroundActivityStartOptions(context) ?: return@runCatching false
                context.startActivity(intent, options)
                logDebug("started deep link (BAL): $uriForLog via ${context.javaClass.simpleName}")
                true
            }.getOrDefault(false)
            if (balStarted) return true
            if (tryStartViaPendingIntent(context, intent, uriForLog)) return true
            return false
        }
        return runCatching {
            context.startActivity(intent)
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
