package com.lastasylum.alliance.game

import android.content.Context
import android.widget.Toast
import com.lastasylum.alliance.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Bridge to Last Asylum in-game search / profile / map via [globalphslink] + clipboard.
 *
 * RE (Frida Gadget, v1.0.77): deep links hit InvokeDeepLinkActivated → flyWorldLua;
 * profile/alliance use LookPlayerSimpleInfoC2S / QueryUnionPublicInfoC2S inside the game client.
 */
object GameSearchBridge {
    enum class SearchKind {
        PLAYER,
        ALLIANCE,
    }

    data class SearchHit(
        val displayName: String,
        val kind: SearchKind,
        val gameRef: String = displayName,
        val mapX: Int? = null,
        val mapY: Int? = null,
    )

    suspend fun search(
        context: Context,
        kind: SearchKind,
        query: String,
        serverNumber: Int?,
    ): Result<List<SearchHit>> {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return Result.failure(IllegalArgumentException("query_too_short"))
        }
        if (serverNumber == null || serverNumber < 1) {
            return Result.failure(IllegalStateException("no_active_server"))
        }
        withContext(Dispatchers.Main) {
            val appContext = context.applicationContext
            GameDeepLinkNavigator.bringGameToForeground(appContext)
            GameDeepLinkNavigator.copyToClipboard(
                appContext,
                clipLabel = "game_search_query",
                text = trimmed,
            )
            delay(GameDeepLinkNavigator.CLIPBOARD_SETTLE_MS)
            val launched = GameDeepLinkNavigator.openFirstMatching(
                appContext,
                GameSearchDeepLinks.searchUrls(kind, trimmed, serverNumber),
            )
            if (launched) {
                toast(appContext, R.string.overlay_game_search_sent_to_game)
            }
        }
        return Result.success(
            listOf(
                SearchHit(
                    displayName = trimmed,
                    kind = kind,
                ),
            ),
        )
    }

    fun openProfile(context: Context, hit: SearchHit, serverNumber: Int?) {
        val appContext = context.applicationContext
        GameDeepLinkNavigator.openWithClipboard(
            context = appContext,
            clipLabel = "game_search_profile",
            clipText = hit.displayName,
            uris = GameSearchDeepLinks.profileUrls(hit.kind, hit.displayName, serverNumber),
        ) { launched ->
            if (launched) {
                toast(appContext, R.string.overlay_game_search_profile_sent)
            } else {
                toast(appContext, R.string.overlay_game_search_bridge_pending)
            }
        }
    }

    fun openOnMap(context: Context, hit: SearchHit, serverNumber: Int?) {
        val x = hit.mapX
        val y = hit.mapY
        if (x != null && y != null) {
            GameMapNavigator.open(context, x, y, serverNumber)
            return
        }
        val appContext = context.applicationContext
        GameDeepLinkNavigator.openWithClipboard(
            context = appContext,
            clipLabel = "game_search_map",
            clipText = hit.displayName,
            uris = GameSearchDeepLinks.mapUrlsForName(hit.kind, hit.displayName, serverNumber),
        ) { launched ->
            if (launched) {
                toast(appContext, R.string.overlay_game_search_map_sent)
            } else {
                toast(appContext, R.string.overlay_game_search_bridge_pending)
            }
        }
    }

    private fun toast(context: Context, resId: Int) {
        Toast.makeText(context.applicationContext, context.getString(resId), Toast.LENGTH_SHORT).show()
    }
}
