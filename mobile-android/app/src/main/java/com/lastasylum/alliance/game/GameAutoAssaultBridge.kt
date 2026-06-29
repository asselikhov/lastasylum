package com.lastasylum.alliance.game

import android.content.Context
import android.content.Intent
import android.util.Log
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.overlay.AllianceRosterCache
import org.json.JSONArray
import org.json.JSONObject

/**
 * Auto-assault bridge: broadcasts rally auto-join config to the patched game's Frida bridge
 * (via [MapFlyReceiver]). The receiver writes [squadrelay_autoassault.json], which the bridge
 * polls and uses to join eligible alliance rallies.
 */
object GameAutoAssaultBridge {
    private const val TAG = "GameAutoAssaultBridge"
    private const val EXTRA_AUTO_ASSAULT = "autoassault"
    private const val EXTRA_AA_CONFIG = "aaConfig"

    fun sync(context: Context): Boolean {
        val prefs = UserSettingsPreferences(context.applicationContext)
        return write(context, prefs)
    }

    fun write(context: Context, prefs: UserSettingsPreferences): Boolean {
        val appContext = context.applicationContext
        val json = buildConfigJson(prefs)
        var sent = false
        for (pkg in GameDeepLinkNavigator.targetPackages(appContext)) {
            val trimmed = pkg.trim()
            if (trimmed.isEmpty() || !isInstalled(appContext, trimmed)) continue
            val status = GameMapPatchStatus.read(appContext, listOf(trimmed))
            if (status.state != GameMapPatchStatus.State.PATCH_READY &&
                status.state != GameMapPatchStatus.State.PATCH_OUTDATED
            ) {
                continue
            }
            val intent = Intent(GameMapFlyBridge.ACTION_MAP_FLY).apply {
                setPackage(trimmed)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(EXTRA_AUTO_ASSAULT, true)
                putExtra(EXTRA_AA_CONFIG, json.toString())
            }
            runCatching {
                appContext.sendBroadcast(intent)
                sent = true
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "autoassault broadcast pkg=$trimmed enabled=${prefs.isAutoAssaultEnabled()}")
                }
            }.onFailure { e -> Log.w(TAG, "autoassault broadcast failed pkg=$trimmed", e) }
        }
        return sent
    }

    private fun buildConfigJson(prefs: UserSettingsPreferences): JSONObject {
        val allowedIds = prefs.getAutoAssaultAllowedMemberIds()
        val allowedNames = resolveAllowedCreatorNames(prefs, allowedIds)
        val selectedSquads = prefs.getAutoAssaultSquads()
        val squads = JSONArray()
        for (idx in UserSettingsPreferences.AUTO_ASSAULT_SQUAD_MIN..UserSettingsPreferences.AUTO_ASSAULT_SQUAD_MAX) {
            if (!selectedSquads.contains(idx)) continue
            squads.put(
                JSONObject()
                    .put("index", idx)
                    .put("powerMin", prefs.getAutoAssaultSquadPowerMin(idx))
                    .put("powerMax", prefs.getAutoAssaultSquadPowerMax(idx)),
            )
        }
        val names = JSONArray()
        allowedNames.forEach { names.put(it) }
        val ids = JSONArray()
        allowedIds.forEach { ids.put(it) }
        val types = JSONArray()
        prefs.getAutoAssaultTargetTypes().forEach { types.put(it) }
        return JSONObject()
            .put("enabled", prefs.isAutoAssaultEnabled())
            .put("maxDistance", prefs.getAutoAssaultMaxDistance())
            .put("minRemainingSec", prefs.getAutoAssaultMinRemainingSec())
            .put("cooldownSec", prefs.getAutoAssaultCooldownSec())
            .put("maxConcurrent", prefs.getAutoAssaultMaxConcurrent())
            .put("disableAtEpochMs", prefs.getAutoAssaultDisableAtMs())
            .put("targetTypes", types)
            .put("levelMin", prefs.getAutoAssaultTargetLevelMin())
            .put("levelMax", prefs.getAutoAssaultTargetLevelMax())
            .put("squads", squads)
            .put("allowedNames", names)
            .put("allowedUserIds", ids)
    }

    /** Игровые ники создателей штурма: username из игрового ростера альянса (по playerId). */
    private fun resolveAllowedCreatorNames(prefs: UserSettingsPreferences, allowedIds: Set<String>): List<String> {
        if (allowedIds.isEmpty()) return emptyList()
        val roster = AllianceRosterCache.peek().takeIf { it.isNotEmpty() }
            ?: AllianceRosterCache.parse(prefs.getAllianceRosterJson())
        return roster
            .filter { allowedIds.contains(it.userId) }
            .map { it.username.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
}
