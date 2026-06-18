package com.lastasylum.alliance.ui.screens.teamnews

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayPollVotersSheetHost

private object TeamNewsRoutes {
    const val LIST = "news_list"
    const val CREATE = "news_create"
    fun detail(id: String) = "news_detail/$id"
    fun edit(id: String) = "news_edit/$id"
}

@Composable
fun TeamNewsNavHost(
    teamId: String,
    currentUserId: String,
    myTeamRole: String,
    canPublishNews: Boolean,
    teamsRepository: TeamsRepository,
    modifier: Modifier = Modifier,
    sectionActive: Boolean = true,
    onNewsInboxChanged: () -> Unit = {},
) {
    val overlayUi = LocalOverlayUiMode.current
    val nav = rememberNavController()
    val navHost = @Composable {
        NavHost(
            navController = nav,
            startDestination = TeamNewsRoutes.LIST,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(TeamNewsRoutes.LIST) {
                TeamNewsListScreen(
                    teamId = teamId,
                    currentUserId = currentUserId,
                    canPublishNews = canPublishNews,
                    teamsRepository = teamsRepository,
                    sectionActive = sectionActive,
                    onOpenDetail = { nav.navigate(TeamNewsRoutes.detail(it)) },
                    onCreate = { nav.navigate(TeamNewsRoutes.CREATE) },
                )
            }
            composable(
                route = "news_detail/{newsId}",
                arguments = listOf(navArgument("newsId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("newsId")
                if (id.isNullOrBlank()) {
                    TeamNewsNavInvalidArgs(onBack = { nav.popBackStack() })
                    return@composable
                }
                TeamNewsDetailRoute(
                    teamId = teamId,
                    newsId = id,
                    currentUserId = currentUserId,
                    myTeamRole = myTeamRole,
                    teamsRepository = teamsRepository,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(TeamNewsRoutes.edit(it)) },
                    onNewsInboxChanged = onNewsInboxChanged,
                )
            }
            composable(TeamNewsRoutes.CREATE) {
                TeamNewsEditorScreen(
                    teamId = teamId,
                    newsId = null,
                    teamsRepository = teamsRepository,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() },
                    onNewsInboxChanged = onNewsInboxChanged,
                )
            }
            composable(
                route = "news_edit/{newsId}",
                arguments = listOf(navArgument("newsId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("newsId")
                if (id.isNullOrBlank()) {
                    TeamNewsNavInvalidArgs(onBack = { nav.popBackStack() })
                    return@composable
                }
                TeamNewsEditorScreen(
                    teamId = teamId,
                    newsId = id,
                    teamsRepository = teamsRepository,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
    if (overlayUi) {
        OverlayPollVotersSheetHost(modifier = modifier) {
            navHost()
        }
    } else {
        Box(modifier = modifier) {
            navHost()
        }
    }
}
