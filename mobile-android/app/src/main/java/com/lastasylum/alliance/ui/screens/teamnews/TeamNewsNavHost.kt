package com.lastasylum.alliance.ui.screens.teamnews

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.teams.TeamNewsReadCursorSync
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayPollVotersSheetHost

private object TeamNewsRoutes {
    const val LIST = "news_list"
    const val CREATE = "news_create"
    const val PREVIEW = "news_preview"
    fun detail(id: String) = "news_detail/$id"
    fun edit(id: String) = "news_edit/$id"
}

private const val NEWS_MARK_READ_LIST_KEY = "list"

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
    onRegisterMarkReadAction: ((() -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val overlayUi = LocalOverlayUiMode.current
    val app = remember(context) { AppContainer.from(context.applicationContext) }
    val nav = rememberNavController()
    var listRefreshNonce by remember { mutableIntStateOf(0) }
    var markReadHandlers by remember { mutableStateOf<Map<String, () -> Unit>>(emptyMap()) }
    val authorUsername = remember {
        JwtAccessTokenClaims.username(app.tokenStore.getAccessToken())
            ?: JwtAccessTokenClaims.email(app.tokenStore.getAccessToken())
            ?: "Вы"
    }
    val registerMarkReadAction: (String, (() -> Unit)?) -> Unit = { key, action ->
        markReadHandlers = if (action == null) {
            markReadHandlers - key
        } else {
            markReadHandlers + (key to action)
        }
    }
    val navBackStackEntry by nav.currentBackStackEntryAsState()
    val activeMarkReadAction = remember(markReadHandlers, navBackStackEntry) {
        when (navBackStackEntry?.destination?.route) {
            TeamNewsRoutes.LIST -> markReadHandlers[NEWS_MARK_READ_LIST_KEY]
            else -> null
        }
    }
    LaunchedEffect(activeMarkReadAction) {
        onRegisterMarkReadAction(activeMarkReadAction)
    }
    LaunchedEffect(overlayUi, navBackStackEntry?.destination?.route) {
        if (!overlayUi) {
            CombatOverlayService.registerOverlayNewsFlushPendingRead(null)
            return@LaunchedEffect
        }
        val route = navBackStackEntry?.destination?.route
        val onList = route == TeamNewsRoutes.LIST
        CombatOverlayService.registerOverlayNewsFlushPendingRead(
            if (onList) {
                {
                    TeamNewsReadCursorSync.flushPendingNewsCursor(
                        teamsRepository = teamsRepository,
                        prefs = app.userSettingsPreferences,
                        teamId = teamId,
                    )
                }
            } else {
                null
            },
        )
    }
    LaunchedEffect(listRefreshNonce) {
        if (listRefreshNonce > 0) onNewsInboxChanged()
    }
    DisposableEffect(overlayUi) {
        if (overlayUi) {
            val bumpListRefresh: () -> Unit = { listRefreshNonce++ }
            CombatOverlayService.registerOverlayNewsRehydrateAction(bumpListRefresh)
            onDispose {
                CombatOverlayService.registerOverlayNewsRehydrateAction(null)
            }
        } else {
            onDispose { }
        }
    }
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
                    refreshNonce = listRefreshNonce,
                    onOpenDetail = { nav.navigate(TeamNewsRoutes.detail(it)) },
                    onCreate = { nav.navigate(TeamNewsRoutes.CREATE) },
                    onProvideMarkReadAction = registerMarkReadAction,
                )
            }
            composable(
                route = "news_detail/{newsId}",
                arguments = listOf(navArgument("newsId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("newsId")
                DisposableEffect(id) {
                    onDispose { listRefreshNonce++ }
                }
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
                    initialDraft = TeamNewsEditorDraftHolder.draft,
                    onBack = {
                        TeamNewsEditorDraftHolder.draft = null
                        nav.popBackStack()
                    },
                    onContinueToPreview = { draft ->
                        TeamNewsEditorDraftHolder.draft = draft
                        nav.navigate(TeamNewsRoutes.PREVIEW)
                    },
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
                    initialDraft = TeamNewsEditorDraftHolder.draft,
                    onBack = {
                        TeamNewsEditorDraftHolder.draft = null
                        nav.popBackStack()
                    },
                    onContinueToPreview = { draft ->
                        TeamNewsEditorDraftHolder.draft = draft
                        nav.navigate(TeamNewsRoutes.PREVIEW)
                    },
                )
            }
            composable(TeamNewsRoutes.PREVIEW) {
                val draft = TeamNewsEditorDraftHolder.draft
                if (draft == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                    return@composable
                }
                val editingExisting = draft.isEdit && !draft.newsId.isNullOrBlank()
                val editingNewsId = draft.newsId
                TeamNewsPublishPreviewScreen(
                    teamId = teamId,
                    draft = draft,
                    authorUserId = currentUserId,
                    authorUsername = authorUsername,
                    teamsRepository = teamsRepository,
                    onBackToEdit = { nav.popBackStack() },
                    onPublished = { publishedId ->
                        listRefreshNonce++
                        nav.navigate(TeamNewsRoutes.detail(publishedId)) {
                            if (editingExisting && editingNewsId != null) {
                                popUpTo(TeamNewsRoutes.detail(editingNewsId)) { inclusive = true }
                            } else {
                                popUpTo(TeamNewsRoutes.LIST) { inclusive = false }
                            }
                            launchSingleTop = true
                        }
                    },
                    onNewsInboxChanged = onNewsInboxChanged,
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
