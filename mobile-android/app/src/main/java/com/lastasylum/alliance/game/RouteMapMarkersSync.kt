package com.lastasylum.alliance.game

import android.content.Context
import android.content.Intent
import android.util.Log
import com.lastasylum.alliance.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Экспорт точек маршрутов в sdcard-файл для игрового моста (метки на карте).
 * Синхронизация между устройствами команды — через backend (будущая фаза).
 */
object RouteMapMarkersSync {
    private const val TAG = "RouteMapMarkersSync"
    private const val MARKERS_SDCARD = "/sdcard/Download/squadrelay_route_markers.json"
    private const val MARKERS_PULSE = "/sdcard/Download/squadrelay_route_markers_pulse.json"
    private const val DEBOUNCE_MS = 400L

    private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingPublish: Runnable? = null
    private var pendingContext: Context? = null
    private var pendingTeamId: String? = null
    private var pendingRoutes: List<RoutePlannerRoute>? = null

    fun publishDebounced(context: Context, teamId: String, routes: List<RoutePlannerRoute>) {
        pendingContext = context.applicationContext
        pendingTeamId = teamId
        pendingRoutes = routes
        pendingPublish?.let { debounceHandler.removeCallbacks(it) }
        val task = Runnable {
            val ctx = pendingContext ?: return@Runnable
            val tid = pendingTeamId ?: return@Runnable
            val data = pendingRoutes ?: return@Runnable
            publishNow(ctx, tid, data)
        }
        pendingPublish = task
        debounceHandler.postDelayed(task, DEBOUNCE_MS)
    }

    fun publish(context: Context, teamId: String, routes: List<RoutePlannerRoute>) {
        publishNow(context.applicationContext, teamId, routes)
    }

    private fun publishNow(context: Context, teamId: String, routes: List<RoutePlannerRoute>) {
        val markers = JSONArray()
        routes.forEach { route ->
            route.points.forEach { point ->
                markers.put(
                    JSONObject().apply {
                        put("id", point.id)
                        put("routeId", route.id)
                        put("routeName", route.name)
                        put("x", point.x)
                        put("y", point.y)
                        put("sid", point.sid)
                        put("memberName", point.memberName)
                        put("label", point.mapLabel(route.name))
                    },
                )
            }
        }
        val payload = JSONObject().apply {
            put("teamId", teamId.trim())
            put("ts", System.currentTimeMillis())
            put("markers", markers)
        }
        runCatching {
            File(MARKERS_SDCARD).writeText(payload.toString())
            File(MARKERS_PULSE).writeText(JSONObject().put("ts", System.currentTimeMillis()).toString())
            logDebug("markers published count=${markers.length()}")
        }.onFailure { e ->
            Log.w(TAG, "markers write failed", e)
        }
        dispatchRefresh(context)
    }

    private fun dispatchRefresh(context: Context) {
        val appContext = context.applicationContext
        for (pkg in GameDeepLinkNavigator.targetPackages(appContext)) {
            val trimmed = pkg.trim()
            if (trimmed.isEmpty()) continue
            val bridgeStatus = GameMapPatchStatus.read(appContext, listOf(trimmed))
            if (bridgeStatus.state != GameMapPatchStatus.State.PATCH_READY) continue
            val intent = Intent(GameMapFlyBridge.ACTION_MAP_FLY).apply {
                setPackage(trimmed)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra("routeMarkers", true)
            }
            runCatching { appContext.sendBroadcast(intent) }
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
