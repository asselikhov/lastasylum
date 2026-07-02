package com.lastasylum.alliance.game

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * R4/R5 — показывать внутриигровую кнопку «Маршрут» рядом с «Перемещение».
 * Frida-мост читает файл при открытии панели перемещения.
 */
object GameRouteOfficerBridge {
    const val SDCARD_CONFIG = "/sdcard/Download/squadrelay_route_officer.json"

    fun sync(context: Context) {
        val enabled = RoutePlannerAccess.canCreateRoutes(context)
        runCatching {
            File(SDCARD_CONFIG).writeText(
                JSONObject()
                    .put("enabled", enabled)
                    .put("ts", System.currentTimeMillis())
                    .toString(),
            )
        }
    }
}
