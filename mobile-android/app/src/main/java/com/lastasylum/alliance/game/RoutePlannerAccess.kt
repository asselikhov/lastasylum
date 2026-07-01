package com.lastasylum.alliance.game

import android.content.Context
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.OverlayTeamContextCache

/** Права на создание маршрутов: ранг R4/R5 в команде приложения (player team squad role). */
object RoutePlannerAccess {
    fun canCreateRoutes(context: Context): Boolean = isSquadOfficer(resolveSquadRole(context))

    /** Любой участник команды с привязанным playerTeamId. */
    fun canViewRoutes(context: Context): Boolean = !resolveTeamId(context).isNullOrBlank()

    fun resolveMyGameNicknames(context: Context): Set<String> {
        val repo = AppContainer.from(context).usersRepository
        val profile = repo.peekMyProfile() ?: repo.peekMyProfileDisk() ?: return emptySet()
        val out = LinkedHashSet<String>()
        profile.activeGameNickname?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        profile.gameIdentities.forEach { identity ->
            identity.gameNickname.trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
        }
        return out
    }

    fun isPointAssignedToMe(context: Context, point: RoutePlannerPoint): Boolean =
        RouteRelocateAllExecutor.filterPointsForCurrentPlayer(context, listOf(point)).isNotEmpty()

    fun resolveTeamId(context: Context): String? {
        OverlayTeamContextCache.peekForPanel()?.teamId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val profile = AppContainer.from(context).usersRepository.peekMyProfile()
            ?: AppContainer.from(context).usersRepository.peekMyProfileDisk()
            ?: return null
        return profile.playerTeamId?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** R4 или R5 в составе команды SquadRelay. */
    fun isSquadOfficer(role: String?): Boolean {
        val r = role?.trim()?.uppercase().orEmpty()
        return r == "R4" || r == "R5"
    }

    fun resolveSquadRole(context: Context): String? {
        OverlayTeamContextCache.peekForPanel()?.myTeamRole
            ?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val repo = AppContainer.from(context).usersRepository
        val profile = repo.peekMyProfile() ?: repo.peekMyProfileDisk() ?: return null

        profile.playerTeamSquadRole?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let { return it }

        val userId = profile.id.trim()
        return OverlayTeamContextCache.peekCachedTeam()
            ?.members
            ?.firstOrNull { it.userId == userId }
            ?.teamRole
            ?.trim()?.uppercase()
            ?.takeIf { it.isNotEmpty() }
    }
}
