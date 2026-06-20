package com.lastasylum.alliance.ui.screens.teamnews

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.ui.chat.MessengerImagesPreviewHost
import com.lastasylum.alliance.ui.components.team.journal.JournalEditorMode
import com.lastasylum.alliance.ui.components.team.journal.JournalEditorTokens
import com.lastasylum.alliance.ui.components.team.journal.JournalImageAttachment
import com.lastasylum.alliance.ui.components.team.journal.JournalImageAttachmentGrid
import com.lastasylum.alliance.ui.components.team.journal.JournalModeChips
import com.lastasylum.alliance.ui.components.team.journal.JournalPollOnlyHint
import com.lastasylum.alliance.ui.components.team.journal.JournalPollOptionEditor
import com.lastasylum.alliance.ui.components.team.journal.JournalPrimaryButton
import com.lastasylum.alliance.ui.components.team.journal.journalEditorBottomBarInsets
import com.lastasylum.alliance.ui.components.team.journal.JournalSectionDivider
import com.lastasylum.alliance.ui.components.team.journal.JournalSectionHeader
import com.lastasylum.alliance.ui.components.team.journal.JournalTextField
import com.lastasylum.alliance.ui.screens.teamnews.resolvedTeamNewsImageUrl
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamNewsEditorScreen(
    teamId: String,
    newsId: String?,
    teamsRepository: TeamsRepository,
    initialDraft: TeamNewsEditorDraft? = null,
    onBack: () -> Unit,
    onContinueToPreview: (TeamNewsEditorDraft) -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val app = remember(context) { AppContainer.from(context.applicationContext) }
    val overlayUi = LocalOverlayUiMode.current
    val scope = rememberCoroutineScope()
    var editorMode by remember { mutableStateOf(initialDraft?.mode ?: JournalEditorMode.News) }
    var title by remember { mutableStateOf(initialDraft?.title.orEmpty()) }
    var body by remember { mutableStateOf(initialDraft?.body.orEmpty()) }
    val attachments = remember {
        mutableStateListOf<EditorAttachment>().apply {
            initialDraft?.attachments?.let { addAll(it) }
        }
    }
    var includePoll by remember { mutableStateOf(initialDraft?.includePoll ?: false) }
    var pollQuestion by remember { mutableStateOf(initialDraft?.pollQuestion.orEmpty()) }
    val pollOptions = remember {
        mutableStateListOf<String>().apply {
            if (initialDraft != null) {
                addAll(initialDraft.pollOptions)
            } else {
                add("")
                add("")
            }
        }
    }
    val pollOptionIds = remember {
        mutableStateListOf<String?>().apply {
            if (initialDraft != null) {
                addAll(initialDraft.pollOptionIds)
            } else {
                add(null)
                add(null)
            }
        }
    }
    var loading by remember { mutableStateOf(newsId != null && initialDraft == null) }
    var err by remember { mutableStateOf<String?>(null) }
    var titleError by remember { mutableStateOf(false) }
    var bodyError by remember { mutableStateOf(false) }
    var pollError by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var imagePreviewStart by remember { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()
    var scrollTarget by remember { mutableStateOf<TeamNewsEditorScrollField?>(null) }
    val isCreate = newsId == null
    val showPollSection = editorMode == JournalEditorMode.PollOnly ||
        (newsId != null && includePoll)

    LaunchedEffect(overlayUi) {
        if (!overlayUi) return@LaunchedEffect
        CombatOverlayService.extendInGameOverlayUiHold()
        while (isActive) {
            delay(2_000L)
            CombatOverlayService.extendInGameOverlayUiHold()
        }
    }

    LaunchedEffect(scrollTarget, editorMode, showPollSection, err) {
        val field = scrollTarget ?: return@LaunchedEffect
        delay(100)
        val index = teamNewsEditorScrollIndex(
            field = field,
            hasErrorBanner = err != null,
            isCreate = isCreate,
            editorMode = editorMode,
            showPollSection = showPollSection,
        )
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
        scrollTarget = null
    }

    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    val pollQuestionFocus = remember { FocusRequester() }
    val pollOptionFocuses = remember { mutableStateListOf<FocusRequester>() }
    LaunchedEffect(pollOptions.size) {
        while (pollOptionFocuses.size < pollOptions.size) {
            pollOptionFocuses.add(FocusRequester())
        }
        while (pollOptionFocuses.size > pollOptions.size) {
            pollOptionFocuses.removeAt(pollOptionFocuses.lastIndex)
        }
    }
    fun requestScroll(field: TeamNewsEditorScrollField) {
        scrollTarget = field
    }

    val fillRequired = stringResource(R.string.team_news_fill_required)
    val pollInvalid = stringResource(R.string.team_news_poll_invalid)
    val maxImagesMsg = stringResource(R.string.team_news_max_images, MAX_TEAM_NEWS_IMAGES)

    var baselineDraft by remember { mutableStateOf(initialDraft) }

    fun currentDraft() = buildTeamNewsEditorDraft(
        mode = editorMode,
        title = title,
        body = body,
        attachments = attachments.toList(),
        includePoll = includePoll,
        pollQuestion = pollQuestion,
        pollOptions = pollOptions.toList(),
        pollOptionIds = pollOptionIds.toList(),
        newsId = newsId,
    )

    fun requestBack() {
        if (loading) return
        val baseline = baselineDraft ?: buildTeamNewsEditorDraft(
            mode = if (newsId == null) JournalEditorMode.News else editorMode,
            title = "",
            body = "",
            attachments = emptyList(),
            includePoll = false,
            pollQuestion = "",
            pollOptions = listOf("", ""),
            pollOptionIds = listOf(null, null),
            newsId = newsId,
        )
        if (currentDraft() != baseline) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = !loading) { requestBack() }

    LaunchedEffect(teamId, newsId) {
        if (initialDraft != null || newsId == null) return@LaunchedEffect
        loading = true
        teamsRepository.getTeamNews(teamId, newsId)
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
                baselineDraft = buildTeamNewsEditorDraft(
                    mode = editorMode,
                    title = title,
                    body = body,
                    attachments = attachments.toList(),
                    includePoll = includePoll,
                    pollQuestion = pollQuestion,
                    pollOptions = pollOptions.toList(),
                    pollOptionIds = pollOptionIds.toList(),
                    newsId = newsId,
                )
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
        if (attachments.count { !it.fileId.isNullOrBlank() } >= MAX_TEAM_NEWS_IMAGES) {
            err = maxImagesMsg
            return@rememberLauncherForActivityResult
        }
        if (overlayUi) {
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
        }
        val placeholderIndex = attachments.size
        attachments.add(EditorAttachment(fileId = null, previewPath = uri.toString(), uploading = true))
        scope.launch {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "image/jpeg"
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                ?: run {
                    attachments.removeAt(placeholderIndex.coerceAtMost(attachments.lastIndex))
                    err = res.getString(R.string.chat_attachment_read_failed)
                    return@launch
                }
            val name = "upload_${System.currentTimeMillis()}.jpg"
            teamsRepository.uploadTeamNewsImage(teamId, bytes, name, mime)
                .onSuccess { uploaded ->
                    if (placeholderIndex < attachments.size) {
                        attachments[placeholderIndex] = EditorAttachment(
                            fileId = uploaded.fileId,
                            previewPath = uploaded.url,
                            uploading = false,
                        )
                    }
                }
                .onFailure { e ->
                    if (placeholderIndex < attachments.size) {
                        attachments[placeholderIndex] = attachments[placeholderIndex].copy(
                            uploading = false,
                            uploadFailed = true,
                        )
                    }
                    err = e.toUserMessageRu(res)
                }
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
                previewRequest = when {
                    att.uploading && att.previewPath?.startsWith("content:") == true -> {
                        coil3.request.ImageRequest.Builder(context).data(att.previewPath).build()
                    }
                    else -> teamNewsAuthedImageRequest(context, att.previewPath)
                },
                uploading = att.uploading,
                uploadFailed = att.uploadFailed,
            )
        }
    }

    val previewUrls = remember(attachments) {
        attachments.mapNotNull { resolvedTeamNewsImageUrl(it.previewPath) }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.team_news_discard_draft)) },
            text = { Text(stringResource(R.string.team_news_discard_draft_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.team_news_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.chat_clear_room_cancel))
                }
            },
        )
    }

    if (imagePreviewStart >= 0 && previewUrls.isNotEmpty()) {
        MessengerImagesPreviewHost(
            urls = previewUrls,
            startIndex = imagePreviewStart.coerceIn(0, previewUrls.lastIndex),
            onDismiss = { imagePreviewStart = -1 },
        )
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
                    IconButton(onClick = { requestBack() }) {
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
                JournalPrimaryButton(
                    label = stringResource(R.string.team_news_continue),
                    onClick = {
                        val validation = validateTeamNewsEditorDraft(
                            mode = editorMode,
                            title = title,
                            body = body,
                            includePoll = includePoll,
                            pollQuestion = pollQuestion,
                            pollOptions = pollOptions.toList(),
                            attachments = attachments.toList(),
                            fillRequiredMessageRes = R.string.team_news_fill_required,
                            pollInvalidMessageRes = R.string.team_news_poll_invalid,
                            maxImagesMessageRes = R.string.team_news_max_images,
                            isEdit = newsId != null,
                        )
                        titleError = TeamNewsEditorFieldError.Title in validation.fieldErrors
                        bodyError = TeamNewsEditorFieldError.Body in validation.fieldErrors
                        pollError = TeamNewsEditorFieldError.Poll in validation.fieldErrors
                        if (!validation.isValid) {
                            err = validation.messageRes?.let { res.getString(it) }
                                ?: if (attachments.any { it.uploading }) {
                                    res.getString(R.string.team_news_saving)
                                } else {
                                    null
                                }
                            return@JournalPrimaryButton
                        }
                        err = null
                        onContinueToPreview(currentDraft())
                    },
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .journalEditorBottomBarInsets(overlayUi)
                        .padding(horizontal = JournalEditorTokens.screenHorizontalPad, vertical = 12.dp),
                )
            }
        },
    ) { pad ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = JournalEditorTokens.screenHorizontalPad,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(JournalEditorTokens.sectionGap),
        ) {
            err?.let { msg ->
                item(key = "err") {
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (newsId == null) {
                item(key = "mode_chips") {
                    JournalModeChips(
                        selected = editorMode,
                        onSelect = { mode ->
                            editorMode = mode
                            when (mode) {
                                JournalEditorMode.PollOnly -> includePoll = true
                                JournalEditorMode.News -> {
                                    includePoll = false
                                    pollQuestion = ""
                                    pollOptions.clear()
                                    pollOptions.add("")
                                    pollOptions.add("")
                                    pollOptionIds.clear()
                                    pollOptionIds.add(null)
                                    pollOptionIds.add(null)
                                }
                            }
                        },
                        newsLabel = stringResource(R.string.team_news_editor_mode_news),
                        pollLabel = stringResource(R.string.team_news_editor_mode_poll),
                    )
                }
            }
            if (editorMode == JournalEditorMode.PollOnly) {
                item(key = "poll_hint") {
                    JournalPollOnlyHint(
                        text = stringResource(R.string.team_news_poll_only_hint),
                    )
                }
            }
            if (editorMode == JournalEditorMode.News) {
                item(key = "title_section") {
                    JournalSectionHeader(stringResource(R.string.team_news_title_hint))
                    JournalTextField(
                        value = title,
                        onValueChange = {
                            title = it.take(200)
                            titleError = false
                        },
                        label = stringResource(R.string.team_news_title_hint),
                        singleLine = true,
                        isError = titleError,
                        errorMessage = if (titleError) fillRequired else null,
                        hint = "${title.length}/200",
                        focusRequester = titleFocus,
                        onFocused = { requestScroll(TeamNewsEditorScrollField.Title) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }),
                    )
                }
                item(key = "body_section") {
                    JournalSectionHeader(stringResource(R.string.team_news_body_hint))
                    JournalTextField(
                        value = body,
                        onValueChange = {
                            body = it
                            bodyError = false
                        },
                        label = stringResource(R.string.team_news_body_hint),
                        minLines = 6,
                        isError = bodyError,
                        errorMessage = if (bodyError) fillRequired else null,
                        focusRequester = bodyFocus,
                        onFocused = { requestScroll(TeamNewsEditorScrollField.Body) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                    )
                }
                item(key = "images_section") {
                    JournalSectionHeader(stringResource(R.string.team_news_images_section))
                    JournalImageAttachmentGrid(
                        attachments = journalAttachments,
                        onAdd = { launchImagePicker() },
                        onRemove = { index -> attachments.removeAt(index) },
                        addLabel = stringResource(R.string.team_news_pick_image),
                        enabled = !attachments.any { it.uploading },
                        maxImages = MAX_TEAM_NEWS_IMAGES,
                        onPreview = { index ->
                            if (index < previewUrls.size) imagePreviewStart = index
                        },
                    )
                }
            }
            if (showPollSection) {
                item(key = "poll_divider") {
                    if (editorMode == JournalEditorMode.PollOnly) {
                        JournalSectionDivider()
                    }
                    JournalSectionHeader(stringResource(R.string.team_news_poll_section))
                }
                item(key = "poll_question") {
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
                        errorMessage = if (pollError) pollInvalid else null,
                        focusRequester = pollQuestionFocus,
                        onFocused = { requestScroll(TeamNewsEditorScrollField.PollQuestion) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { pollOptionFocuses.firstOrNull()?.requestFocus() },
                        ),
                    )
                }
                item(key = "poll_options") {
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
                        onOptionFocused = { requestScroll(TeamNewsEditorScrollField.PollOptions) },
                        focusRequesters = pollOptionFocuses,
                    )
                    if (pollOptions.size < 8) {
                        TextButton(
                            onClick = {
                                pollOptions.add("")
                                pollOptionIds.add(null)
                            },
                        ) {
                            Text(stringResource(R.string.team_news_add_option))
                        }
                    }
                }
            }
            item(key = "bottom_spacer") {
                Box(Modifier.padding(bottom = 8.dp))
            }
        }
    }
}
