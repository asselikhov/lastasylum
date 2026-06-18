package com.lastasylum.alliance.ui.screens.teamnews

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.lastasylum.alliance.data.teams.CreateTeamNewsBody
import com.lastasylum.alliance.data.teams.TeamNewsPollCreateBody
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.teams.UpdateTeamNewsBody
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.ui.components.team.journal.JournalEditorMode
import com.lastasylum.alliance.ui.components.team.journal.JournalEditorTokens
import com.lastasylum.alliance.ui.components.team.journal.JournalImageAttachment
import com.lastasylum.alliance.ui.components.team.journal.JournalImageAttachmentGrid
import com.lastasylum.alliance.ui.components.team.journal.JournalModeChips
import com.lastasylum.alliance.ui.components.team.journal.JournalPollOptionEditor
import com.lastasylum.alliance.ui.components.team.journal.JournalPrimaryButton
import com.lastasylum.alliance.ui.components.team.journal.JournalTextField
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.launch

private data class EditorAttachment(
    val fileId: String,
    val previewPath: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamNewsEditorScreen(
    teamId: String,
    newsId: String?,
    teamsRepository: TeamsRepository,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onNewsInboxChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val res = context.resources
    val app = remember(context) { AppContainer.from(context.applicationContext) }
    val overlayUi = LocalOverlayUiMode.current
    val scope = rememberCoroutineScope()
    var editorMode by remember { mutableStateOf(JournalEditorMode.News) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<EditorAttachment>() }
    var includePoll by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    val pollOptions = remember { mutableStateListOf("", "") }
    val pollOptionIds = remember { mutableStateListOf<String?>(null, null) }
    var loading by remember { mutableStateOf(newsId != null) }
    var saving by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var titleError by remember { mutableStateOf(false) }
    var bodyError by remember { mutableStateOf(false) }
    var pollError by remember { mutableStateOf(false) }

    if (overlayUi) {
        BackHandler(enabled = !saving) { onBack() }
    }

    LaunchedEffect(teamId, newsId) {
        val nid = newsId ?: return@LaunchedEffect
        loading = true
        teamsRepository.getTeamNews(teamId, nid)
            .onSuccess { d ->
                title = d.title
                body = d.body
                attachments.clear()
                d.imageRelativeUrls.forEach { rel ->
                    val seg = rel.substringAfterLast('/')
                    if (seg.isNotBlank()) {
                        attachments.add(EditorAttachment(fileId = seg, previewPath = rel))
                    }
                }
                val p = d.poll
                if (p != null) {
                    includePoll = true
                    pollQuestion = p.question
                    pollOptions.clear()
                    pollOptionIds.clear()
                    p.options.forEach { opt ->
                        pollOptions.add(opt.text)
                        pollOptionIds.add(opt.id)
                    }
                    val pollOnlyPost =
                        d.pollOnly ||
                            (d.body.trim().isEmpty() && d.imageRelativeUrls.isEmpty())
                    if (pollOnlyPost) {
                        editorMode = JournalEditorMode.PollOnly
                    }
                }
                loading = false
            }
            .onFailure { e ->
                err = e.toUserMessageRu(res)
                loading = false
            }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (overlayUi) {
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
        }
        scope.launch {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "image/jpeg"
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                ?: run {
                    err = res.getString(R.string.chat_attachment_read_failed)
                    return@launch
                }
            val name = "upload_${System.currentTimeMillis()}.jpg"
            teamsRepository.uploadTeamNewsImage(teamId, bytes, name, mime)
                .onSuccess { uploaded ->
                    attachments.add(
                        EditorAttachment(
                            fileId = uploaded.fileId,
                            previewPath = uploaded.url,
                        ),
                    )
                }
                .onFailure { e -> err = e.toUserMessageRu(res) }
        }
    }

    fun launchImagePicker() {
        if (overlayUi) {
            OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
        }
        pickImage.launch("image/*")
    }

    val journalAttachments = remember(attachments.toList(), context) {
        attachments.map { att ->
            JournalImageAttachment(
                fileId = att.fileId,
                previewRequest = teamNewsAuthedImageRequest(context, att.previewPath),
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Text(
                        if (newsId == null) stringResource(R.string.team_news_new)
                        else stringResource(R.string.team_news_edit),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.team_news_cd_back),
                        )
                    }
                },
            )
        },
    ) { pad ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .then(if (!overlayUi) Modifier.imePadding() else Modifier),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(JournalEditorTokens.sectionGap),
            ) {
                err?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (newsId == null) {
                    JournalModeChips(
                        selected = editorMode,
                        onSelect = { mode ->
                            editorMode = mode
                            if (mode == JournalEditorMode.PollOnly) {
                                includePoll = true
                            }
                        },
                        newsLabel = stringResource(R.string.team_news_editor_mode_news),
                        pollLabel = stringResource(R.string.team_news_editor_mode_poll),
                    )
                }
                if (editorMode == JournalEditorMode.PollOnly) {
                    Text(
                        text = stringResource(R.string.team_news_poll_only_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (editorMode == JournalEditorMode.News) {
                    JournalTextField(
                        value = title,
                        onValueChange = {
                            title = it.take(200)
                            titleError = false
                        },
                        label = stringResource(R.string.team_news_title_hint),
                        singleLine = true,
                        isError = titleError,
                        hint = "${title.length}/200",
                    )
                    JournalTextField(
                        value = body,
                        onValueChange = {
                            body = it
                            bodyError = false
                        },
                        label = stringResource(R.string.team_news_body_hint),
                        minLines = 6,
                        isError = bodyError,
                    )
                    JournalImageAttachmentGrid(
                        attachments = journalAttachments,
                        onAdd = { launchImagePicker() },
                        onRemove = { index -> attachments.removeAt(index) },
                        addLabel = stringResource(R.string.team_news_pick_image),
                        enabled = !saving,
                    )
                    if (includePoll) {
                        TextButton(onClick = { includePoll = false }) {
                            Text(stringResource(R.string.team_news_remove_poll))
                        }
                    } else if (newsId == null) {
                        TextButton(onClick = { includePoll = true }) {
                            Text(stringResource(R.string.team_news_editor_mode_poll))
                        }
                    }
                }
                if (editorMode == JournalEditorMode.PollOnly || includePoll) {
                    JournalTextField(
                        value = pollQuestion,
                        onValueChange = {
                            pollQuestion = it.take(500)
                            pollError = false
                        },
                        label = stringResource(R.string.team_news_poll_question_hint),
                        singleLine = false,
                        minLines = 2,
                        isError = pollError,
                    )
                    val pollOptionLabels = pollOptions.indices.map { i ->
                        stringResource(R.string.team_news_poll_option_hint, i + 1)
                    }
                    JournalPollOptionEditor(
                        options = pollOptions.toList(),
                        optionLabels = pollOptionLabels,
                        onOptionChange = { index, value ->
                            pollOptions[index] = value.take(200)
                            pollError = false
                        },
                        onRemove = { index ->
                            pollOptions.removeAt(index)
                            pollOptionIds.removeAt(index)
                        },
                        enabled = !saving,
                    )
                    if (pollOptions.size < 8) {
                        TextButton(
                            onClick = {
                                pollOptions.add("")
                                pollOptionIds.add(null)
                            },
                            enabled = !saving,
                        ) {
                            Text(stringResource(R.string.team_news_add_option))
                        }
                    }
                }
            }
            JournalPrimaryButton(
                label = stringResource(R.string.team_news_save),
                onClick = {
                    val pollOnly = editorMode == JournalEditorMode.PollOnly
                    val wantsPoll = pollOnly || includePoll
                    titleError = false
                    bodyError = false
                    pollError = false
                    if (wantsPoll) {
                        val filled = pollOptions.count { o -> o.isNotBlank() }
                        if (pollQuestion.isBlank() || filled < 2) {
                            pollError = true
                            err = res.getString(R.string.team_news_poll_invalid)
                            return@JournalPrimaryButton
                        }
                    }
                    if (!pollOnly && title.isBlank()) {
                        titleError = true
                        err = res.getString(R.string.team_news_fill_required)
                        return@JournalPrimaryButton
                    }
                    if (!pollOnly && body.isBlank()) {
                        bodyError = true
                        err = res.getString(R.string.team_news_fill_required)
                        return@JournalPrimaryButton
                    }
                    err = null
                    saving = true
                    scope.launch {
                        val pollBody =
                            if (wantsPoll) {
                                val optionPairs = pollOptions.indices.mapNotNull { i ->
                                    val text = pollOptions[i].trim()
                                    if (text.isEmpty()) null else text to pollOptionIds.getOrNull(i)
                                }
                                TeamNewsPollCreateBody(
                                    question = pollQuestion.trim(),
                                    optionTexts = optionPairs.map { it.first },
                                    optionIds = if (newsId != null) {
                                        optionPairs.map { (_, id) -> id.orEmpty() }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                null
                            }
                        val publishTitle =
                            if (pollOnly) null else title.trim().takeIf { it.isNotEmpty() }
                        val publishBody =
                            if (pollOnly) null else body.trim().takeIf { it.isNotEmpty() }
                        val publishImages = if (pollOnly) emptyList() else attachments.map { it.fileId }
                        if (newsId == null) {
                            teamsRepository.createTeamNews(
                                teamId,
                                CreateTeamNewsBody(
                                    title = publishTitle,
                                    body = publishBody,
                                    imageFileIds = publishImages,
                                    poll = pollBody,
                                ),
                            )
                                .onSuccess { created ->
                                    TeamNewsReadCursorSync.markNewsSeen(
                                        app.teamsRepository,
                                        app.userSettingsPreferences,
                                        teamId,
                                        created.createdAt,
                                    )
                                    onNewsInboxChanged()
                                    CombatOverlayService.notifyOverlayTeamNewsActivity()
                                    saving = false
                                    onDone()
                                }
                                .onFailure { e ->
                                    err = e.toUserMessageRu(res)
                                    saving = false
                                }
                        } else {
                            teamsRepository.updateTeamNews(
                                teamId,
                                newsId,
                                UpdateTeamNewsBody(
                                    title = if (pollOnly) null else title.trim().ifBlank { null },
                                    body = if (pollOnly) null else body.trim(),
                                    imageFileIds = if (pollOnly) emptyList() else attachments.map { it.fileId },
                                    poll = if (wantsPoll) pollBody else null,
                                    clearPoll = if (!wantsPoll) true else null,
                                ),
                            )
                                .onSuccess { saving = false; onDone() }
                                .onFailure { e ->
                                    err = e.toUserMessageRu(res)
                                    saving = false
                                }
                        }
                    }
                },
                enabled = !saving,
                loading = saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}
