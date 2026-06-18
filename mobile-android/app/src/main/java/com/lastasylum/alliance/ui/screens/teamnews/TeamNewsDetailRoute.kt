package com.lastasylum.alliance.ui.screens.teamnews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.teams.TeamNewsReadCursorSync
import com.lastasylum.alliance.data.teams.TeamNewsDetailDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayAwareAlertDialog
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.launch

@Composable
internal fun TeamNewsNavInvalidArgs(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.team_news_load_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.team_news_cd_back))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamNewsDetailRoute(
    teamId: String,
    newsId: String,
    currentUserId: String,
    myTeamRole: String,
    teamsRepository: TeamsRepository,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onNewsInboxChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val res = context.resources
    val app = remember(context) { AppContainer.from(context.applicationContext) }
    val overlayUi = LocalOverlayUiMode.current
    var detail by remember { mutableStateOf<TeamNewsDetailDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var voteBusy by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (overlayUi) {
        BackHandler { onBack() }
    }

    LaunchedEffect(teamId, newsId) {
        loading = true
        err = null
        teamsRepository.getTeamNews(teamId, newsId)
            .onSuccess { doc ->
                detail = doc
                TeamNewsReadCursorSync.flushPendingNewsCursor(
                    app.teamsRepository,
                    app.userSettingsPreferences,
                    teamId,
                )
                TeamNewsReadCursorSync.markNewsSeen(
                    app.teamsRepository,
                    app.userSettingsPreferences,
                    teamId,
                    doc.createdAt,
                )
                onNewsInboxChanged()
                loading = false
            }
            .onFailure { e ->
                err = e.toUserMessageRu(res)
                loading = false
            }
    }

    val d = detail
    val canEdit = d != null && (
        d.authorUserId == currentUserId ||
            myTeamRole == "R4" || myTeamRole == "R5"
        )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TeamNewsDetailTopBar(
                title = d?.poll?.question?.takeIf { it.isNotBlank() }
                    ?: d?.title
                    ?: "",
                canEdit = canEdit,
                onBack = onBack,
                onEdit = { d?.let { onEdit(it.id) } },
                onDelete = {
                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                    deleteOpen = true
                },
            )
        },
    ) { pad ->
        when {
            loading && d == null -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            err != null && d == null -> {
                Text(err ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(pad))
            }
            d != null -> {
                TeamNewsDetailScrollContent(
                    detail = d,
                    voteBusy = voteBusy,
                    onVote = { oid ->
                        voteBusy = true
                        scope.launch {
                            teamsRepository.voteTeamNews(teamId, newsId, oid)
                                .onSuccess { voted -> detail = voted }
                                .onFailure { e -> err = e.toUserMessageRu(res) }
                            voteBusy = false
                        }
                    },
                    modifier = Modifier
                        .padding(pad)
                        .fillMaxSize(),
                )
            }
        }
    }

    if (deleteOpen && d != null) {
        OverlayModalScope(preparedByCaller = true) {
            OverlayAwareAlertDialog(
                onDismissRequest = { deleteOpen = false },
                title = { Text(stringResource(R.string.team_news_delete)) },
                text = { Text(stringResource(R.string.team_news_delete_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                teamsRepository.deleteTeamNews(teamId, d.id)
                                    .onSuccess { deleteOpen = false; onBack() }
                                    .onFailure { e -> err = e.toUserMessageRu(res) }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.team_news_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteOpen = false }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }
    }
}
