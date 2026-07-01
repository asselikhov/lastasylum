package com.lastasylum.alliance.game

import android.content.Context
import android.util.Log
import com.lastasylum.alliance.data.teams.PutTeamRoutePlannerBody
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Синхронизация маршрутов команды с backend (источник правды для всех устройств). */
object RoutePlannerSync {
    private const val TAG = "RoutePlannerSync"

    suspend fun pullIfNewer(context: Context, teamId: String): Boolean = withContext(Dispatchers.IO) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return@withContext false
        val repo = AppContainer.from(context).teamsRepository
        val snapshot = repo.getRoutePlanner(tid).getOrElse { e ->
            Log.w(TAG, "pull failed", e)
            return@withContext false
        }
        val localRevision = RoutePlannerStore.localRevisionMs(context, tid)
        if (snapshot.updatedAtMs <= localRevision) return@withContext false
        val routes = snapshot.toDomainRoutes()
        RoutePlannerStore.replaceAll(context, tid, routes, snapshot.updatedAtMs)
        true
    }

    suspend fun push(context: Context, teamId: String): Boolean = withContext(Dispatchers.IO) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return@withContext false
        if (!RoutePlannerAccess.canCreateRoutes(context)) return@withContext false
        val routes = RoutePlannerStore.list(context, tid)
        val body = PutTeamRoutePlannerBody(
            routes = routes.toWire(),
            clientUpdatedAtMs = RoutePlannerStore.localRevisionMs(context, tid),
        )
        val repo = AppContainer.from(context).teamsRepository
        val snapshot = repo.putRoutePlanner(tid, body).getOrElse { e ->
            Log.w(TAG, "push failed", e)
            return@withContext false
        }
        RoutePlannerStore.setLocalRevision(context, tid, snapshot.updatedAtMs)
        val serverRoutes = snapshot.toDomainRoutes()
        RoutePlannerStore.replaceAll(context, tid, serverRoutes, snapshot.updatedAtMs, publishMarkers = true)
        true
    }
}
