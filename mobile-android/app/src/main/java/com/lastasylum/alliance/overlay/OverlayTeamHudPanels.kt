package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.teams.TeamNewsMarkRead
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.screens.teamforum.TeamForumNavHost
import com.lastasylum.alliance.ui.screens.teamnews.TeamNewsNavHost
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun OverlayTeamHudLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}

@Composable
private fun OverlayTeamHudError(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .padding(SquadRelayDimens.contentPaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun OverlayTeamHudScaffold(
    modifier: Modifier = Modifier,
    forceReload: Boolean = false,
    content: @Composable (OverlayTeamHudContext) -> Unit,
) {
    val context = LocalContext.current
    val app = remember { AppContainer.from(context.applicationContext) }
    val res = context.resources
    val cachedInitially = remember { OverlayTeamContextCache.peekValid() }
    var loading by remember { mutableStateOf(cachedInitially == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var hudContext by remember { mutableStateOf(cachedInitially) }

    LaunchedEffect(forceReload) {
        val hadCache = hudContext != null
        if (forceReload) {
            hudContext = null
            loading = true
            error = null
        } else if (!hadCache) {
            loading = true
            error = null
        }
        val loaded = withContext(Dispatchers.IO) {
            OverlayTeamContextCache.load(
                usersRepository = app.usersRepository,
                teamsRepository = app.teamsRepository,
                forceRefresh = forceReload,
            )
        }
        loaded.onSuccess { hudContext = it }
            .onFailure { e ->
                if (hudContext == null) {
                    error = e.toUserMessageRu(res)
                }
            }
        loading = false
    }

    Box(modifier.fillMaxSize()) {
        when {
            loading -> OverlayTeamHudLoading(Modifier.fillMaxSize())
            error != null -> OverlayTeamHudError(error!!, Modifier.fillMaxSize())
            hudContext != null -> content(hudContext!!)
        }
    }
}

@Composable
fun OverlayTeamNewsPanel(
    currentUserId: String,
    teamsRepository: TeamsRepository,
    modifier: Modifier = Modifier,
    onNewsInboxChanged: () -> Unit = {},
    onRegisterMarkReadAction: ((() -> Unit)?) -> Unit = {},
) {
    OverlayTeamHudScaffold(modifier = modifier) { ctx ->
        val context = LocalContext.current
        val app = remember { AppContainer.from(context.applicationContext) }
        val scope = rememberCoroutineScope()
        val uid = currentUserId.ifBlank { ctx.currentUserId }
        LaunchedEffect(ctx.teamId, uid) {
            onRegisterMarkReadAction {
                scope.launch {
                    TeamNewsMarkRead.markAllNewsRead(
                        teamsRepository = teamsRepository,
                        prefs = app.userSettingsPreferences,
                        teamId = ctx.teamId,
                        currentUserId = uid,
                    )
                    onNewsInboxChanged()
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose { onRegisterMarkReadAction(null) }
        }
        Box(Modifier.fillMaxSize()) {
            TeamNewsNavHost(
                teamId = ctx.teamId,
                currentUserId = currentUserId.ifBlank { ctx.currentUserId },
                myTeamRole = ctx.myTeamRole,
                canPublishNews = ctx.canPublishNews,
                teamsRepository = teamsRepository,
                modifier = Modifier.fillMaxSize(),
                onNewsInboxChanged = onNewsInboxChanged,
            )
        }
    }
}

@Composable
fun OverlayTeamForumPanel(
    currentUserId: String,
    teamsRepository: TeamsRepository,
    modifier: Modifier = Modifier,
    onForumInboxChanged: () -> Unit = {},
    onRegisterMarkReadAction: ((() -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember { AppContainer.from(context.applicationContext) }
    OverlayTeamHudScaffold(modifier = modifier) { ctx ->
        Box(Modifier.fillMaxSize()) {
            TeamForumNavHost(
                teamId = ctx.teamId,
                currentUserId = currentUserId.ifBlank { ctx.currentUserId },
                canManageTopics = ctx.canManageForumTopics,
                canModerateForumMessages = ctx.canModerateForumMessages,
                teamsRepository = teamsRepository,
                forumSocket = app.teamForumSocket,
                tokenStore = app.tokenStore,
                enabledStickerPackKeys = ctx.enabledStickerPackKeys,
                modifier = Modifier.fillMaxSize(),
                onForumInboxChanged = onForumInboxChanged,
                onRegisterMarkReadAction = onRegisterMarkReadAction,
            )
        }
    }
}
