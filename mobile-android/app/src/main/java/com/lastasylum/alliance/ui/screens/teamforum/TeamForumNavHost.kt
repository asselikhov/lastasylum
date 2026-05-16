package com.lastasylum.alliance.ui.screens.teamforum

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lastasylum.alliance.ui.chat.ChatBubbleAuthorHeader
import com.lastasylum.alliance.ui.chat.ChatIncomingAvatarEndPad
import com.lastasylum.alliance.ui.chat.ChatIncomingAvatarSize
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.chat.TelegramImageCaptionBar
import com.lastasylum.alliance.ui.chat.chatBubbleShapeIncoming
import com.lastasylum.alliance.ui.chat.chatBubbleShapeOutgoing
import com.lastasylum.alliance.ui.chat.TelegramLikeAttachmentsGrid
import com.lastasylum.alliance.ui.theme.roleAccentColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumMessageDeletedEvent
import com.lastasylum.alliance.data.teams.TeamForumSocketManager
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamForumTypingEvent
import com.lastasylum.alliance.data.teams.TeamsRepository
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import com.lastasylum.alliance.ui.chat.MessageSheetActionRow
import com.lastasylum.alliance.ui.chat.MessageSheetDividerSpaced
import com.lastasylum.alliance.ui.chat.MessageSheetPreviewSurface
import com.lastasylum.alliance.ui.chat.chatAuthedImageRequest
import com.lastasylum.alliance.ui.chat.replyPreviewText
import com.lastasylum.alliance.ui.util.copyForumMessageToClipboard
import com.lastasylum.alliance.ui.util.forumMessageHasCopyableContent
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.ui.chat.ChatStickerFormat
import com.lastasylum.alliance.ui.chat.chatDayKey
import com.lastasylum.alliance.ui.chat.formatChatDaySeparator
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMuted
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMutedIncoming
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import java.io.File
import java.io.InputStream
import java.util.UUID
import android.content.ContentResolver
import android.os.ParcelFileDescriptor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold

private object ForumRoutes {
    const val LIST = "forum_list"
    fun topic(id: String) = "forum_topic/$id"
}

private fun formatForumTime(iso: String): String =
    runCatching {
        val instant = Instant.parse(iso)
        val fmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale("ru"))
        fmt.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrElse { iso }

@Composable
fun TeamForumNavHost(
    teamId: String,
    currentUserId: String,
    canManageTopics: Boolean,
    /** РўРѕР»СЊРєРѕ R5 РјРѕР¶РµС‚ СЂРµРґР°РєС‚РёСЂРѕРІР°С‚СЊ/СѓРґР°Р»СЏС‚СЊ С‡СѓР¶РёРµ СЃРѕРѕР±С‰РµРЅРёСЏ (РєР°Рє РЅР° Р±СЌРєРµРЅРґРµ). */
    canModerateForumMessages: Boolean,
    teamsRepository: TeamsRepository,
    forumSocket: TeamForumSocketManager,
    tokenStore: TokenStore,
    modifier: Modifier = Modifier,
    forumTabReselectSignal: Int = 0,
    /** Wire keys of sticker packs the current user may send. */
    enabledStickerPackKeys: Set<String> = emptySet(),
) {
    val nav = rememberNavController()
    val topicTitles = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(forumTabReselectSignal) {
        if (forumTabReselectSignal > 0) {
            nav.popBackStack(ForumRoutes.LIST, inclusive = false)
        }
    }
    NavHost(
        navController = nav,
        startDestination = ForumRoutes.LIST,
        modifier = modifier,
    ) {
        composable(ForumRoutes.LIST) {
            TeamForumListRoute(
                teamId = teamId,
                canManageTopics = canManageTopics,
                teamsRepository = teamsRepository,
                topicTitles = topicTitles,
                onOpenTopic = { t ->
                    topicTitles[t.id] = t.title
                    nav.navigate(ForumRoutes.topic(t.id))
                },
                onBack = { },
            )
        }
        composable(
            route = "forum_topic/{topicId}",
            arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
        ) { entry ->
            val topicId = entry.arguments?.getString("topicId") ?: return@composable
            val title = topicTitles[topicId].orEmpty()
            TeamForumTopicChatRoute(
                teamId = teamId,
                topicId = topicId,
                topicTitle = title,
                currentUserId = currentUserId,
                canModerateMessages = canModerateForumMessages,
                teamsRepository = teamsRepository,
                forumSocket = forumSocket,
                tokenStore = tokenStore,
                enabledStickerPackKeys = enabledStickerPackKeys,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

@Composable
private fun TeamForumListRoute(
    teamId: String,
    canManageTopics: Boolean,
    teamsRepository: TeamsRepository,
    topicTitles: MutableMap<String, String>,
    onOpenTopic: (TeamForumTopicDto) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val topics = remember { mutableStateListOf<TeamForumTopicDto>() }
    var menuTopic by remember { mutableStateOf<TeamForumTopicDto?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }
    var createBusy by remember { mutableStateOf(false) }
    var editTopic by remember { mutableStateOf<TeamForumTopicDto?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }
    var deleteTopic by remember { mutableStateOf<TeamForumTopicDto?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            teamsRepository.listForumTopics(teamId)
                .onSuccess {
                    topics.clear()
                    topics.addAll(it)
                    it.forEach { t -> topicTitles[t.id] = t.title }
                }
                .onFailure { e -> error = e.toUserMessageRu(res) }
            loading = false
        }
    }

    LaunchedEffect(teamId) {
        reload()
    }

    Column(Modifier.fillMaxSize()) {
        error?.let { err ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SquadRelayDimens.itemGap, vertical = SquadRelayDimens.itemGap),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            ) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                loading && topics.isEmpty() -> {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        strokeWidth = 2.dp,
                    )
                }
                topics.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.team_forum_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 88.dp, top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(topics, key = { it.id }) { t ->
                            OutlinedCard(
                                onClick = { onOpenTopic(t) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                ),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = SquadRelaySurfaces.subtleColor(0.48f),
                                ),
                                elevation = CardDefaults.outlinedCardElevation(defaultElevation = 6.dp),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                ) {
                                    Box(
                                        Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.tertiary,
                                                    ),
                                                ),
                                            ),
                                    )
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                                            modifier = Modifier.size(26.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = t.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.team_forum_topic_meta,
                                                    t.messageCount,
                                                    t.lastMessageAt?.let { formatForumTime(it) }
                                                        ?: "вЂ”",
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (canManageTopics) {
                                            Box {
                                                IconButton(onClick = { menuTopic = t }) {
                                                    Icon(
                                                        Icons.Filled.MoreVert,
                                                        contentDescription = stringResource(
                                                            R.string.team_forum_topic_menu_cd,
                                                        ),
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = menuTopic?.id == t.id,
                                                    onDismissRequest = { menuTopic = null },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.team_forum_edit_topic)) },
                                                        onClick = {
                                                            menuTopic = null
                                                            editTopic = t
                                                            editTitle = t.title
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.team_forum_delete_topic)) },
                                                        onClick = {
                                                            menuTopic = null
                                                            deleteTopic = t
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (canManageTopics) {
                FloatingActionButton(
                    onClick = {
                        createTitle = ""
                        showCreate = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.team_forum_new_topic_cd))
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!createBusy) showCreate = false },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.team_forum_new_topic_title)) },
            text = {
                OutlinedTextField(
                    value = createTitle,
                    onValueChange = { createTitle = it.take(200) },
                    label = { Text(stringResource(R.string.team_forum_topic_title_label)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !createBusy,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !createBusy && createTitle.trim().isNotEmpty(),
                    onClick = {
                        scope.launch {
                            createBusy = true
                            teamsRepository.createForumTopic(teamId, createTitle)
                                .onSuccess {
                                    topicTitles[it.id] = it.title
                                    showCreate = false
                                    reload()
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            createBusy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.profile_action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!createBusy) showCreate = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    editTopic?.let { topic ->
        AlertDialog(
            onDismissRequest = { if (!editBusy) editTopic = null },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.team_forum_edit_topic_title)) },
            text = {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it.take(200) },
                    label = { Text(stringResource(R.string.team_forum_topic_title_label)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editBusy,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !editBusy && editTitle.trim().isNotEmpty(),
                    onClick = {
                        scope.launch {
                            editBusy = true
                            teamsRepository.updateForumTopic(teamId, topic.id, editTitle)
                                .onSuccess {
                                    topicTitles[it.id] = it.title
                                    editTopic = null
                                    reload()
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            editBusy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.profile_action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!editBusy) editTopic = null }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    deleteTopic?.let { topic ->
        AlertDialog(
            onDismissRequest = { deleteTopic = null },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.team_forum_delete_topic_title)) },
            text = { Text(stringResource(R.string.team_forum_delete_topic_body, topic.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            teamsRepository.deleteForumTopic(teamId, topic.id)
                                .onSuccess {
                                    deleteTopic = null
                                    reload()
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                        }
                    },
                ) {
                    Text(stringResource(R.string.team_forum_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTopic = null }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamForumTopicChatRoute(
    teamId: String,
    topicId: String,
    @Suppress("UNUSED_PARAMETER") topicTitle: String,
    currentUserId: String,
    canModerateMessages: Boolean,
    teamsRepository: TeamsRepository,
    forumSocket: TeamForumSocketManager,
    tokenStore: TokenStore,
    onBack: () -> Unit,
    enabledStickerPackKeys: Set<String> = emptySet(),
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val messages = remember { mutableStateListOf<TeamForumMessageDto>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var pendingImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingImageUrl by remember { mutableStateOf<String?>(null) }
    var pendingImageFileIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var uploadingImage by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var typingHint by remember { mutableStateOf<String?>(null) }
    val sortedMessages by remember {
        derivedStateOf { messages.sortedBy { it.createdAt } }
    }

    var editMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var editBody by remember { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var activeActionMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var deletingSelection by remember { mutableStateOf(false) }
    var highlightMessageId by remember { mutableStateOf<String?>(null) }
    var remoteImagePreview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImages = remember {
        { urls: List<String>, idx: Int -> remoteImagePreview = urls to idx }
    }

    fun clearPendingAttachment() {
        pendingImageUris = emptyList()
        pendingImageUrl = null
        pendingImageFileIds = emptyList()
    }

    fun applyEdited(msg: TeamForumMessageDto) {
        val i = messages.indexOfFirst { it.id == msg.id }
        if (i >= 0) {
            messages[i] = msg
        } else {
            messages.add(msg)
        }
    }

    fun applyDeleted(ev: TeamForumMessageDeletedEvent) {
        val i = messages.indexOfFirst { it.id == ev.messageId }
        if (i >= 0) {
            messages[i] = messages[i].copy(
                text = "",
                imageRelativeUrl = null,
                deletedAt = ev.deletedAt ?: messages[i].deletedAt,
                deletedByUserId = ev.deletedByUserId ?: messages[i].deletedByUserId,
            )
        }
    }

    fun mergeNew(msg: TeamForumMessageDto) {
        if (messages.any { it.id == msg.id }) return
        messages.add(msg)
    }

    fun canDeleteForumMessage(msg: TeamForumMessageDto): Boolean {
        val deleted = !msg.deletedAt.isNullOrBlank() &&
            !msg.deletedAt.equals("null", ignoreCase = true)
        if (deleted) return false
        return msg.senderUserId == currentUserId || canModerateMessages
    }

    fun uploadPickedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            uploadingImage = true
            error = null
            val result = uploadForumImagesFromUris(context, res, teamsRepository, teamId, uris)
            uploadingImage = false
            result.onSuccess { (ids, preview) ->
                pendingImageFileIds = ids
                pendingImageUrl = preview
            }
            result.onFailure { e ->
                pendingImageFileIds = emptyList()
                pendingImageUrl = null
                error = e.toUserMessageRu(res)
            }
        }
    }

    LaunchedEffect(teamId, topicId) {
        loading = true
        error = null
        messages.clear()
        clearPendingAttachment()
        draft = ""
        teamsRepository.listForumMessages(teamId, topicId, before = null, limit = 50)
            .onSuccess { page -> messages.addAll(page) }
            .onFailure { e -> error = e.toUserMessageRu(res) }
        loading = false
    }

    LaunchedEffect(sortedMessages.size, sortedMessages.lastOrNull()?.id) {
        if (sortedMessages.isNotEmpty()) {
            val lastIndex = 1 + sortedMessages.lastIndex
            runCatching { listState.animateScrollToItem(lastIndex) }
        }
    }

    LaunchedEffect(typingHint) {
        val hint = typingHint ?: return@LaunchedEffect
        delay(4000)
        if (typingHint == hint) typingHint = null
    }

    DisposableEffect(teamId, topicId) {
        val onNew: (TeamForumMessageDto) -> Unit = { mergeNew(it) }
        val onEdited: (TeamForumMessageDto) -> Unit = { applyEdited(it) }
        val onDeleted: (TeamForumMessageDeletedEvent) -> Unit = { applyDeleted(it) }
        val onTyping: (TeamForumTypingEvent) -> Unit = { ev ->
            if (ev.userId != currentUserId) {
                typingHint = context.getString(R.string.team_forum_typing, ev.username)
            }
        }
        forumSocket.addMessageListener(onNew)
        forumSocket.addMessageEditedListener(onEdited)
        forumSocket.addMessageDeletedListener(onDeleted)
        forumSocket.addTypingListener(onTyping)
        forumSocket.connect(
            BuildConfig.API_BASE_URL,
            teamId,
            topicId,
        ) { tokenStore.getAccessToken() }
        onDispose {
            forumSocket.removeMessageListener(onNew)
            forumSocket.removeMessageEditedListener(onEdited)
            forumSocket.removeMessageDeletedListener(onDeleted)
            forumSocket.removeTypingListener(onTyping)
            forumSocket.disconnect()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = SquadRelayDimens.keyboardComposerGap),
    ) {
        error?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        typingHint?.let { hint ->
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        if (selectedMessageIds.isNotEmpty()) {
            ForumSelectionToolbar(
                selectedCount = selectedMessageIds.size,
                isDeleting = deletingSelection,
                onClear = { if (!deletingSelection) selectedMessageIds = emptySet() },
                onDelete = { if (!deletingSelection) confirmBulkDelete = true },
            )
        }
        Box(
            Modifier.weight(1f),
        ) {
            if (loading && messages.isEmpty()) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    strokeWidth = 2.dp,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(
                        sortedMessages,
                        key = { _, m -> m.id },
                    ) { idx, msg ->
                        val prev = sortedMessages.getOrNull(idx - 1)
                        val dayCurr = chatDayKey(msg.createdAt)
                        val dayPrev = chatDayKey(prev?.createdAt)
                        if (idx == 0 || dayCurr != dayPrev) {
                            val sep = formatChatDaySeparator(msg.createdAt)
                            if (sep.isNotBlank()) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val sch = MaterialTheme.colorScheme
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = SquadRelaySurfaces.subtleColor(0.48f),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 4.dp,
                                        border = BorderStroke(1.dp, sch.outline.copy(alpha = 0.18f)),
                                    ) {
                                        Text(
                                            text = sep,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                        val mine = msg.senderUserId == currentUserId
                        val inSelectionMode = selectedMessageIds.isNotEmpty()
                        val isSelected = msg.id in selectedMessageIds
                        val canDeleteMsg = canDeleteForumMessage(msg)
                        ForumMessageBubble(
                            message = msg,
                            sortedMessages = sortedMessages,
                            messageIndex = idx,
                            isMine = mine,
                            canDelete = canDeleteMsg,
                            inSelectionMode = inSelectionMode,
                            isSelected = isSelected,
                            highlighted = highlightMessageId == msg.id,
                            onOpenImages = openImages,
                            onJumpToMessage = { targetId ->
                                val targetIndex = sortedMessages.indexOfFirst { it.id == targetId }
                                if (targetIndex >= 0) {
                                    scope.launch {
                                        runCatching { listState.animateScrollToItem(targetIndex) }
                                        highlightMessageId = targetId
                                        delay(900)
                                        if (highlightMessageId == targetId) highlightMessageId = null
                                    }
                                }
                            },
                            onBeginSelection = {
                                selectedMessageIds = setOf(msg.id)
                            },
                            onToggleSelection = {
                                selectedMessageIds =
                                    if (isSelected) selectedMessageIds - msg.id else selectedMessageIds + msg.id
                            },
                            onSwipeReply = {
                                if (msg.deletedAt.isNullOrBlank()) {
                                    replyToMessage = msg
                                }
                            },
                            onOpenActions = {
                                if (msg.deletedAt.isNullOrBlank()) {
                                    activeActionMessageId = msg.id
                                }
                            },
                        )
                    }
                }
            }
        }
        ForumTopicComposer(
            draft = draft,
            onDraftChange = { draft = it },
            replyTo = replyToMessage,
            onClearReply = { replyToMessage = null },
            pendingImageUris = pendingImageUris,
            onClearPendingImage = { clearPendingAttachment() },
            pendingImageRemotePreviewUrl = pendingImageUrl,
            postedImageFileIds = pendingImageFileIds,
            isSending = sending,
            isUploadingImage = uploadingImage,
            sendEnabled = true,
            canUseZlobyakaStickers = enabledStickerPackKeys.contains(ZlobyakaStickerPack.PACK_KEY),
            onSend = {
                scope.launch {
                    val trimmed = draft.trim()
                    if (trimmed.isEmpty() && pendingImageFileIds.isEmpty()) {
                        return@launch
                    }
                    if (uploadingImage) return@launch
                    sending = true
                    val textForApi = trimmed.ifBlank {
                        if (pendingImageFileIds.isNotEmpty()) " " else ""
                    }
                    teamsRepository.postForumMessage(
                        teamId,
                        topicId,
                        textForApi,
                        replyToMessageId = replyToMessage?.id,
                        imageFileId = null,
                        imageFileIds = pendingImageFileIds.takeIf { it.isNotEmpty() },
                    )
                        .onSuccess {
                            mergeNew(it)
                            draft = ""
                            replyToMessage = null
                            clearPendingAttachment()
                        }
                        .onFailure { e -> error = e.toUserMessageRu(res) }
                    sending = false
                }
            },
            onSendStickerPayload = { payload ->
                scope.launch {
                    sending = true
                    clearPendingAttachment()
                    teamsRepository.postForumMessage(
                        teamId = teamId,
                        topicId = topicId,
                        text = payload,
                        replyToMessageId = replyToMessage?.id,
                        imageFileId = null,
                        imageFileIds = null,
                    )
                        .onSuccess { mergeNew(it) }
                        .onFailure { e -> error = e.toUserMessageRu(res) }
                    sending = false
                    replyToMessage = null
                }
            },
            onImageUrisPicked = { uris ->
                val merged = (pendingImageUris + uris).distinct().take(12)
                pendingImageUris = merged
                uploadPickedImages(merged)
            },
            onTyping = { forumSocket.emitTyping() },
        )
    }

    remoteImagePreview?.let { (urls, start) ->
        if (urls.isNotEmpty()) {
            ForumImagesPreviewOverlay(
                modifier = Modifier.fillMaxSize(),
                urls = urls,
                startIndex = start,
                onDismiss = { remoteImagePreview = null },
            )
        }
    }

    // legacy menu dialog removed (replaced with bottom sheet)

    editMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { if (!editBusy) editMessage = null },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.team_forum_edit_message_title)) },
            text = {
                OutlinedTextField(
                    value = editBody,
                    onValueChange = { editBody = it.take(4000) },
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editBusy,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !editBusy &&
                        (
                            editBody.trim().isNotEmpty() ||
                                msg.imageRelativeUrls.isNotEmpty() ||
                                !msg.imageRelativeUrl.isNullOrBlank()
                            ),
                    onClick = {
                        scope.launch {
                            editBusy = true
                            teamsRepository.patchForumMessage(teamId, topicId, msg.id, editBody)
                                .onSuccess {
                                    applyEdited(it)
                                    editMessage = null
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            editBusy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.profile_action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!editBusy) editMessage = null }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    val activeActionMessage = remember(activeActionMessageId, messages) {
        activeActionMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    }
    if (selectedMessageIds.isEmpty()) {
        activeActionMessage?.let { msg ->
            ForumMessageActionsSheet(
                message = msg,
                canEdit = (msg.senderUserId == currentUserId || canModerateMessages) &&
                    msg.deletedAt.isNullOrBlank() &&
                    (
                        msg.text.isNotBlank() ||
                            msg.imageRelativeUrls.isNotEmpty() ||
                            !msg.imageRelativeUrl.isNullOrBlank()
                        ),
                canDelete = canDeleteForumMessage(msg),
                canForward = msg.deletedAt.isNullOrBlank(),
                onDismiss = { activeActionMessageId = null },
                onReply = {
                    replyToMessage = msg
                    activeActionMessageId = null
                },
                onEdit = {
                    editMessage = msg
                    editBody = msg.text
                    activeActionMessageId = null
                },
                onDelete = {
                    activeActionMessageId = null
                    scope.launch {
                        teamsRepository.deleteForumMessage(teamId, topicId, msg.id)
                            .onSuccess {
                                applyDeleted(
                                    TeamForumMessageDeletedEvent(
                                        teamId = teamId,
                                        topicId = topicId,
                                        messageId = msg.id,
                                        deletedAt = java.time.Instant.now().toString(),
                                        deletedByUserId = currentUserId,
                                    ),
                                )
                            }
                            .onFailure { e -> error = e.toUserMessageRu(res) }
                    }
                },
                onForward = {
                    activeActionMessageId = null
                    scope.launch {
                        teamsRepository.forwardForumMessage(teamId, topicId, msg.id)
                            .onSuccess { mergeNew(it) }
                            .onFailure { e -> error = e.toUserMessageRu(res) }
                    }
                },
            )
        }
    }

    if (confirmBulkDelete && selectedMessageIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!deletingSelection) confirmBulkDelete = false },
            containerColor = SquadRelaySurfaces.dialogColor(),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(R.string.chat_bulk_delete_title)) },
            text = {
                Text(stringResource(R.string.chat_bulk_delete_body, selectedMessageIds.size))
            },
            confirmButton = {
                TextButton(
                    enabled = !deletingSelection,
                    onClick = {
                        scope.launch {
                            deletingSelection = true
                            val ids = selectedMessageIds.toList()
                            teamsRepository.bulkDeleteForumMessages(teamId, topicId, ids)
                                .onSuccess {
                                    val now = java.time.Instant.now().toString()
                                    ids.forEach { mid ->
                                        applyDeleted(
                                            TeamForumMessageDeletedEvent(
                                                teamId = teamId,
                                                topicId = topicId,
                                                messageId = mid,
                                                deletedAt = now,
                                                deletedByUserId = currentUserId,
                                            ),
                                        )
                                    }
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            selectedMessageIds = emptySet()
                            confirmBulkDelete = false
                            deletingSelection = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.chat_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deletingSelection,
                    onClick = { confirmBulkDelete = false },
                ) { Text(stringResource(R.string.chat_delete_cancel)) }
            },
        )
    }
}

private suspend fun uploadForumImagesFromUris(
    context: android.content.Context,
    res: android.content.res.Resources,
    teamsRepository: TeamsRepository,
    teamId: String,
    uris: List<Uri>,
): Result<Pair<List<String>, String?>> = withContext(Dispatchers.IO) {
    val cr = context.contentResolver
    val cacheDir = context.cacheDir
    val fileIds = mutableListOf<String>()
    var lastPreview: String? = null
    for (uri in uris) {
        val tmp = File.createTempFile("forum_upload_${UUID.randomUUID()}", ".part", cacheDir)
        try {
            val input = openUriInputStream(cr, uri)
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        res.getString(R.string.chat_attachment_read_failed),
                    ),
                )
            input.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
            if (tmp.length() == 0L) continue
            val header = ByteArray(32)
            tmp.inputStream().use { it.read(header) }
            val sniffed = sniffImageMimeFromHeader(header)
            val mime = resolveUploadImageMime(cr.getType(uri)?.trim().orEmpty(), sniffed)
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        res.getString(R.string.chat_attachment_unsupported),
                    ),
                )
            val bytes = tmp.readBytes()
            val name =
                "forum_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.${guessExt(mime)}"
            val uploaded = teamsRepository.uploadForumImage(teamId, bytes, name, mime).getOrElse { err ->
                return@withContext Result.failure(err)
            }
            fileIds.add(uploaded.fileId)
            lastPreview = uploaded.url
        } finally {
            runCatching { tmp.delete() }
        }
    }
    Result.success(fileIds to lastPreview)
}

private fun openUriInputStream(cr: ContentResolver, uri: Uri): InputStream? {
    runCatching { cr.openInputStream(uri) }.getOrNull()?.let { return it }
    val pfd = runCatching { cr.openFileDescriptor(uri, "r") }.getOrNull()
    if (pfd != null) {
        runCatching { return ParcelFileDescriptor.AutoCloseInputStream(pfd) }
        runCatching { pfd.close() }
    }
    val afd = runCatching { cr.openAssetFileDescriptor(uri, "r") }.getOrNull()
    if (afd != null) {
        runCatching { return afd.createInputStream() }
        runCatching { afd.close() }
    }
    return null
}

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

private fun sniffImageMimeFromHeader(bytes: ByteArray): String? {
    if (bytes.size < 12) return null
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    if (bytes.hasPrefix(jpeg)) return "image/jpeg"
    val png = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    if (bytes.hasPrefix(png)) return "image/png"
    if (bytes.size >= 6) {
        val gif = String(bytes, 0, 6, Charsets.US_ASCII)
        if (gif == "GIF87a" || gif == "GIF89a") return "image/gif"
    }
    val riff = String(bytes, 0, 4, Charsets.US_ASCII)
    val webp = String(bytes, 8, 4, Charsets.US_ASCII)
    if (riff == "RIFF" && webp == "WEBP") return "image/webp"
    if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "image/bmp"
    if (bytes.size >= 12 &&
        bytes[4] == 'f'.code.toByte() &&
        bytes[5] == 't'.code.toByte() &&
        bytes[6] == 'y'.code.toByte() &&
        bytes[7] == 'p'.code.toByte()
    ) {
        val brand = String(bytes, 8, 4, Charsets.US_ASCII)
        if (brand.equals("heic", ignoreCase = true) ||
            brand.equals("heix", ignoreCase = true) ||
            brand.equals("mif1", ignoreCase = true) ||
            brand.equals("msf1", ignoreCase = true)
        ) {
            return "image/heic"
        }
    }
    return null
}

private fun resolveUploadImageMime(declared: String, sniffed: String?): String? {
    val d = declared.trim()
    val dl = d.lowercase(Locale.ROOT)
    return when {
        dl.startsWith("image/") && dl != "image/*" -> d
        dl == "image/*" -> sniffed ?: "image/jpeg"
        sniffed != null -> sniffed
        else -> null
    }
}

private fun guessExt(mime: String): String = when (mime.lowercase(Locale.ROOT)) {
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/bmp" -> "bmp"
    "image/heic" -> "heic"
    else -> "jpg"
}

@Composable
private fun ForumSelectionToolbar(
    selectedCount: Int,
    isDeleting: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = SquadRelaySurfaces.panelColor(0.52f),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.18f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClear, enabled = !isDeleting) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                }
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, enabled = !isDeleting) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = scheme.error,
                    )
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumMessageActionsSheet(
    message: TeamForumMessageDto,
    canEdit: Boolean,
    canDelete: Boolean,
    canForward: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
) {
    val context = LocalContext.current
    val stickerStem = remember(message.text) { ZlobyakaStickerPack.parseStem(message.text) }
    val canCopy = forumMessageHasCopyableContent(message)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                    vertical = SquadRelayDimens.itemGap,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MessageSheetPreviewSurface {
                Text(
                    text = message.senderUsername.trim().ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                when {
                    stickerStem != null -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(ZlobyakaStickerPack.assetUriForStem(stickerStem))
                                    .size(200)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(R.string.cd_chat_sticker),
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Text(
                                text = replyPreviewText(message.text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    message.text.isNotBlank() -> {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    message.imageRelativeUrls.isNotEmpty() ||
                        !message.imageRelativeUrl.isNullOrBlank() -> {
                        Text(
                            text = stringResource(R.string.chat_copy_image_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.chat_sheet_preview_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            MessageSheetDividerSpaced()
            MessageSheetActionRow(
                icon = Icons.Outlined.ContentCopy,
                label = stringResource(R.string.chat_action_copy),
                onClick = {
                    copyForumMessageToClipboard(context, message)
                    onDismiss()
                },
                enabled = canCopy,
            )
            MessageSheetActionRow(
                icon = Icons.AutoMirrored.Outlined.Reply,
                label = stringResource(R.string.chat_action_reply),
                onClick = onReply,
            )
            MessageSheetActionRow(
                icon = Icons.Outlined.ContentPaste,
                label = stringResource(R.string.chat_action_forward),
                onClick = onForward,
                enabled = canForward,
            )
            MessageSheetActionRow(
                icon = Icons.Outlined.Edit,
                label = stringResource(R.string.chat_action_edit),
                onClick = onEdit,
                enabled = canEdit,
            )
            MessageSheetActionRow(
                icon = Icons.Outlined.DeleteOutline,
                label = stringResource(R.string.chat_action_delete),
                onClick = onDelete,
                enabled = canDelete,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}


@Composable
private fun ForumImagesPreviewOverlay(
    modifier: Modifier = Modifier,
    urls: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    var index by remember(startIndex, urls) { mutableStateOf(startIndex.coerceIn(0, urls.lastIndex)) }
    val url = urls.getOrNull(index) ?: urls.first()

    var scale by remember(url) { mutableStateOf(1f) }
    var offsetX by remember(url) { mutableStateOf(0f) }
    var offsetY by remember(url) { mutableStateOf(0f) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        OverlayChatInteractionHold.suppressGameForegroundGate = true
        onDispose { OverlayChatInteractionHold.suppressGameForegroundGate = false }
    }
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        fun clampOffsets() {
            if (scale <= 1f) {
                offsetX = 0f
                offsetY = 0f
                return
            }
            val maxX = (wPx * (scale - 1f) / 2f).coerceAtLeast(0f)
            val maxY = (hPx * (scale - 1f) / 2f).coerceAtLeast(0f)
            offsetX = offsetX.coerceIn(-maxX, maxX)
            offsetY = offsetY.coerceIn(-maxY, maxY)
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
            scale = nextScale
            if (scale > 1f) {
                offsetX += panChange.x
                offsetY += panChange.y
            }
            clampOffsets()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(urls, index, scale) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            clampOffsets()
                        },
                    )
                    detectHorizontalDragGestures { change, dragAmount ->
                        if (scale > 1f) return@detectHorizontalDragGestures
                        change.consume()
                        if (kotlin.math.abs(dragAmount) < 14f) return@detectHorizontalDragGestures
                        if (dragAmount > 0 && index > 0) index -= 1
                        if (dragAmount < 0 && index < urls.lastIndex) index += 1
                    }
                }
                .transformable(state = transformState),
        ) {
            AsyncImage(
                model = chatAuthedImageRequest(context, url),
                contentDescription = stringResource(R.string.cd_chat_message_image),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
                contentScale = ContentScale.Fit,
            )
        }
    }
}
