package com.lastasylum.alliance.ui.screens.teamnews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.lastasylum.alliance.data.teams.CreateTeamNewsBody
import com.lastasylum.alliance.data.teams.TeamNewsPollCreateBody
import com.lastasylum.alliance.data.teams.TeamNewsReadCursorSync
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.teams.UpdateTeamNewsBody
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.ui.components.team.journal.JournalEditorMode
import com.lastasylum.alliance.ui.components.team.journal.JournalEditorTokens
import com.lastasylum.alliance.ui.components.team.journal.JournalPrimaryButton
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamNewsPublishPreviewScreen(
    teamId: String,
    draft: TeamNewsEditorDraft,
    authorUserId: String,
    authorUsername: String,
    teamsRepository: TeamsRepository,
    onBackToEdit: () -> Unit,
    onPublished: (newsId: String) -> Unit,
    onNewsInboxChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val res = context.resources
    val app = remember(context) { AppContainer.from(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var publishing by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val previewDetail = remember(draft, teamId, authorUserId, authorUsername) {
        draft.toDetailPreview(teamId, authorUserId, authorUsername)
    }

    BackHandler(enabled = !publishing) { onBackToEdit() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = { Text(stringResource(R.string.team_news_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackToEdit, enabled = !publishing) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.team_news_cd_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = Color.Transparent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = JournalEditorTokens.screenHorizontalPad, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    err?.let { msg ->
                        Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = onBackToEdit,
                        enabled = !publishing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.team_news_edit_draft))
                    }
                    JournalPrimaryButton(
                        label = if (draft.isEdit) {
                            stringResource(R.string.team_news_save_changes)
                        } else {
                            stringResource(R.string.team_news_publish)
                        },
                        onClick = {
                            publishing = true
                            err = null
                            scope.launch {
                                val pollOnly = draft.mode == JournalEditorMode.PollOnly
                                val wantsPoll = pollOnly || draft.includePoll
                                val pollBody = if (wantsPoll) {
                                    val optionPairs = draft.pollOptions.indices.mapNotNull { i ->
                                        val text = draft.pollOptions[i].trim()
                                        if (text.isEmpty()) null else text to draft.pollOptionIds.getOrNull(i)
                                    }
                                    TeamNewsPollCreateBody(
                                        question = draft.pollQuestion.trim(),
                                        optionTexts = optionPairs.map { it.first },
                                        optionIds = if (draft.isEdit) {
                                            optionPairs.map { (_, id) -> id.orEmpty() }
                                        } else {
                                            null
                                        },
                                    )
                                } else {
                                    null
                                }
                                val publishTitle =
                                    if (pollOnly) null else draft.title.trim().takeIf { it.isNotEmpty() }
                                val publishBody =
                                    if (pollOnly) null else draft.body.trim().takeIf { it.isNotEmpty() }
                                val publishImages = if (pollOnly) {
                                    emptyList()
                                } else {
                                    draft.attachments.mapNotNull { it.fileId }
                                }
                                val result = if (draft.isEdit && draft.newsId != null) {
                                    teamsRepository.updateTeamNews(
                                        teamId,
                                        draft.newsId,
                                        UpdateTeamNewsBody(
                                            title = if (pollOnly) null else draft.title.trim().ifBlank { null },
                                            body = if (pollOnly) null else draft.body.trim(),
                                            imageFileIds = publishImages,
                                            poll = if (wantsPoll) pollBody else null,
                                            clearPoll = if (!wantsPoll) true else null,
                                        ),
                                    ).map { draft.newsId }
                                } else {
                                    teamsRepository.createTeamNews(
                                        teamId,
                                        CreateTeamNewsBody(
                                            title = publishTitle,
                                            body = publishBody,
                                            imageFileIds = publishImages,
                                            poll = pollBody,
                                        ),
                                    ).map { it.id }
                                }
                                result
                                    .onSuccess { publishedId ->
                                        TeamNewsReadCursorSync.markNewsSeen(
                                            app.teamsRepository,
                                            app.userSettingsPreferences,
                                            teamId,
                                            previewDetail.createdAt,
                                        )
                                        onNewsInboxChanged()
                                        CombatOverlayService.notifyOverlayTeamNewsActivity()
                                        TeamNewsEditorDraftHolder.draft = null
                                        publishing = false
                                        onPublished(publishedId)
                                    }
                                    .onFailure { e ->
                                        err = e.toUserMessageRu(res)
                                        publishing = false
                                    }
                            }
                        },
                        enabled = !publishing,
                        loading = publishing,
                    )
                }
            }
        },
    ) { pad ->
        Box(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            TeamNewsDetailScrollContent(
                detail = previewDetail,
                voteBusy = false,
                onVote = {},
                isPreview = true,
            )
            if (publishing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
