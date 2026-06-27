package com.lastasylum.alliance.game

import android.content.Context
import android.content.pm.PackageManager
import com.lastasylum.alliance.BuildConfig

/**
 * Detects SquadRelay map bridge meta-data injected by [patch-frida-gadget.ps1].
 */
object GameMapPatchStatus {
    const val META_MAP_BRIDGE = "com.lastasylum.alliance.map_bridge"
    const val META_MAP_BRIDGE_VERSION = "com.lastasylum.alliance.map_bridge_version"
    const val META_MAP_BRIDGE_GAME_VERSION = "com.lastasylum.alliance.map_bridge_game_version"

    enum class State {
        /** Target game package is not installed. */
        GAME_NOT_FOUND,
        /** Store / stock build — no bridge meta-data. */
        PATCH_NOT_INSTALLED,
        /** Bridge installed but built for another game version. */
        PATCH_OUTDATED,
        /** Bridge matches current game version. */
        PATCH_READY,
    }

    data class Status(
        val state: State,
        val gamePackage: String?,
        val gameVersionName: String?,
        val patchBridgeVersion: String?,
        val patchForGameVersion: String?,
        val supportedGameVersion: String,
    ) {
        val isAutoFlyAvailable: Boolean get() = state == State.PATCH_READY
    }

    fun read(context: Context, gamePackages: Iterable<String>): Status {
        val supported = BuildConfig.MAP_BRIDGE_GAME_VERSION.trim()
        val expectedBridge = BuildConfig.MAP_BRIDGE_VERSION.trim()
        for (pkg in gamePackages) {
            val trimmed = pkg.trim()
            if (trimmed.isEmpty()) continue
            val installed = runCatching {
                context.packageManager.getPackageInfo(trimmed, PackageManager.GET_META_DATA)
            }.getOrNull() ?: continue
            val gameVersion = installed.versionName?.trim().orEmpty().ifEmpty { null }
            val meta = installed.applicationInfo?.metaData
            val bridgePresent = meta?.getString(META_MAP_BRIDGE) == "1" ||
                meta?.getInt(META_MAP_BRIDGE, 0) == 1
            // apktool/aapt encodes a pure-integer android:value (e.g. "2") as an int, so getString
            // returns null; fall back to getInt before deciding the bridge is outdated.
            val bridgeVersion = (meta?.getString(META_MAP_BRIDGE_VERSION)?.trim()?.ifEmpty { null })
                ?: meta?.getInt(META_MAP_BRIDGE_VERSION, -1)?.takeIf { it >= 0 }?.toString()
            val patchFor = meta?.getString(META_MAP_BRIDGE_GAME_VERSION)?.trim()?.ifEmpty { null }
            val state = evaluate(
                patchBridgePresent = bridgePresent,
                gameVersionName = gameVersion,
                patchForGameVersion = patchFor,
                supportedGameVersion = supported,
                installedBridgeVersion = bridgeVersion,
                expectedBridgeVersion = expectedBridge,
            )
            return Status(
                state = state,
                gamePackage = trimmed,
                gameVersionName = gameVersion,
                patchBridgeVersion = bridgeVersion,
                patchForGameVersion = patchFor,
                supportedGameVersion = supported,
            )
        }
        return Status(
            state = State.GAME_NOT_FOUND,
            gamePackage = null,
            gameVersionName = null,
            patchBridgeVersion = null,
            patchForGameVersion = null,
            supportedGameVersion = supported,
        )
    }

    internal fun evaluate(
        patchBridgePresent: Boolean,
        gameVersionName: String?,
        patchForGameVersion: String?,
        supportedGameVersion: String,
        installedBridgeVersion: String? = null,
        expectedBridgeVersion: String = "",
    ): State {
        if (!patchBridgePresent) return State.PATCH_NOT_INSTALLED
        val gameVersion = gameVersionName?.trim().orEmpty()
        val patchFor = patchForGameVersion?.trim().orEmpty().takeIf { it != "unknown" }.orEmpty()
        val supported = supportedGameVersion.trim()
        if (patchFor.isNotEmpty() && gameVersion.isNotEmpty() && gameVersion != patchFor) {
            return State.PATCH_OUTDATED
        }
        if (patchFor.isNotEmpty() && supported.isNotEmpty() && patchFor != supported) {
            return State.PATCH_OUTDATED
        }
        // Bridge logic bump: a patch built for the right game version but with an older bridge
        // must still be re-applied so fixes (e.g. share/bookmark hooks) reach the device.
        val expectedBridge = expectedBridgeVersion.trim()
        if (expectedBridge.isNotEmpty() && installedBridgeVersion?.trim().orEmpty() != expectedBridge) {
            return State.PATCH_OUTDATED
        }
        if (gameVersion.isNotEmpty() && supported.isNotEmpty() && gameVersion == supported) {
            return State.PATCH_READY
        }
        return State.PATCH_READY
    }
}
