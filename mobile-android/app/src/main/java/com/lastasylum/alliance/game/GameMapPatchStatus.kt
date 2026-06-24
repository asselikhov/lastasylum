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
            val bridgeVersion = meta?.getString(META_MAP_BRIDGE_VERSION)?.trim()?.ifEmpty { null }
            val patchFor = meta?.getString(META_MAP_BRIDGE_GAME_VERSION)?.trim()?.ifEmpty { null }
            val state = evaluate(
                patchBridgePresent = bridgePresent,
                gameVersionName = gameVersion,
                patchForGameVersion = patchFor,
                supportedGameVersion = supported,
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
    ): State {
        if (!patchBridgePresent) return State.PATCH_NOT_INSTALLED
        val gameVersion = gameVersionName?.trim().orEmpty()
        val patchFor = patchForGameVersion?.trim().orEmpty().takeIf { it != "unknown" }.orEmpty()
        val supported = supportedGameVersion.trim()
        if (gameVersion.isNotEmpty() && supported.isNotEmpty() && gameVersion == supported) {
            return State.PATCH_READY
        }
        if (patchFor.isNotEmpty() && gameVersion.isNotEmpty() && gameVersion != patchFor) {
            return State.PATCH_OUTDATED
        }
        if (patchFor.isNotEmpty() && supported.isNotEmpty() && patchFor != supported) {
            return State.PATCH_OUTDATED
        }
        return State.PATCH_READY
    }
}
