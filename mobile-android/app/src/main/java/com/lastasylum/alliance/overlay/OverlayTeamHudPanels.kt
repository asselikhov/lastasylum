package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.screens.teamforum.TeamForumNavHost
import com.lastasylum.alliance.ui.screens.teamnews.TeamNewsNavHost
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class OverlayTeamHudContext(
    val teamId: String,
    val currentUserId: String,
    val myTeamRole: String,
    val canPublishNews: Boolean,
    val canManageForumTopics: Boolean,
    val canModerateForumMessages: Boolean,
    val enabledStickerPackKeys: Set<String>,
)

@Composable
private fun OverlayHudPanelTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SquadRelayDimens.contentPaddingHorizontal,
                vertical = 8.dp,
            ),
    )
}

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
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable (OverlayTeamHudContext) -> Unit,
) {
    val context = LocalContext.current
    val app = remember { AppContainer.from(context.applicationContext) }
    val res = context.resources
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var hudContext by remember { mutableStateOf<OverlayTeamHudContext?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        hudContext = null
        val loaded = withContext(Dispatchers.IO) {
            loadOverlayTeamHudContext(app.usersRepository, app.teamsRepository)
        }
        loaded.onSuccess { hudContext = it }
            .onFailure { e -> error = e.toUserMessageRu(res) }
        loading = false
    }

    Column(modifier.fillMaxSize()) {
        OverlayHudPanelTitle(title)
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> OverlayTeamHudLoading(Modifier.fillMaxSize())
                error != null -> OverlayTeamHudError(error!!, Modifier.fillMaxSize())
                hudContext != null -> content(hudContext!!)
            }
        }
    }
}

private suspend fun loadOverlayTeamHudContext(
    usersRepository: UsersRepository,
    teamsRepository: TeamsRepository,
): Result<OverlayTeamHudContext> = runCatching {
    val profile = usersRepository.getMyProfile().getOrThrow()
    val teamId = profile.playerTeamId?.trim().orEmpty()
    if (teamId.isEmpty()) {
        error("no_team")
    }
    val team = teamsRepository.getTeam(teamId).getOrThrow()
    val myTeamRole = team.members.find { it.userId == profile.id }?.teamRole ?: "R1"
    OverlayTeamHudContext(
        teamId = teamId,
        currentUserId = profile.id,
        myTeamRole = myTeamRole,
        canPublishNews = myTeamRole == "R4" || myTeamRole == "R5",
        canManageForumTopics = myTeamRole == "R4" || myTeamRole == "R5",
        canModerateForumMessages = myTeamRole == "R5",
        enabledStickerPackKeys = profile.enabledStickerPacks?.toSet() ?: emptySet(),
    )
}

@Composable
fun OverlayTeamNewsPanel(
    currentUserId: String,
    teamsRepository: TeamsRepository,
    modifier: Modifier = Modifier,
) {
    OverlayTeamHudScaffold(
        title = stringResource(R.string.team_tab_news),
        modifier = modifier,
    ) { ctx ->
        Box(Modifier.fillMaxSize()) {
            TeamNewsNavHost(
                teamId = ctx.teamId,
                currentUserId = currentUserId.ifBlank { ctx.currentUserId },
                myTeamRole = ctx.myTeamRole,
                canPublishNews = ctx.canPublishNews,
                teamsRepository = teamsRepository,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun OverlayTeamForumPanel(
    currentUserId: String,
    teamsRepository: TeamsRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember { AppContainer.from(context.applicationContext) }
    OverlayTeamHudScaffold(
        title = stringResource(R.string.team_tab_forum),
        modifier = modifier,
    ) { ctx ->
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
            )
        }
    }
}
