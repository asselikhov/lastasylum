package com.lastasylum.alliance.ui.screens.teamforum

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private object ForumRoutes {
    const val LIST = "forum_list"
    fun topic(id: String) = "forum_topic/$id"
}

private fun formatForumTime(iso: String): String =
    runCatching {
        val instant = Instant.parse(iso)
        val fmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))
        fmt.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrElse { iso }

@Composable
fun TeamForumNavHost(
    teamId: String,
    currentUserId: String,
    canManageTopics: Boolean,
    /** Только R5 может редактировать/удалять чужие сообщения (как на бэкенде). */
    canModerateForumMessages: Boolean,
    teamsRepository: TeamsRepository,
    forumSocket: TeamForumSocketManager,
    tokenStore: TokenStore,
    modifier: Modifier = Modifier,
) {
    val nav = rememberNavController()
    val topicTitles = remember { mutableStateMapOf<String, String>() }
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
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
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
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(topics, key = { it.id }) { t ->
                            Card(
                                onClick = { onOpenTopic(t) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = t.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.team_forum_topic_meta,
                                                t.messageCount,
                                                t.lastMessageAt?.let { formatForumTime(it) }
                                                    ?: "—",
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (canManageTopics) {
                                        Box {
                                            IconButton(onClick = { menuTopic = t }) {
                                                Icon(
                                                    Icons.Default.MoreVert,
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
            if (canManageTopics) {
                FloatingActionButton(
                    onClick = {
                        createTitle = ""
                        showCreate = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.team_forum_new_topic_cd))
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!createBusy) showCreate = false },
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
    topicTitle: String,
    currentUserId: String,
    canModerateMessages: Boolean,
    teamsRepository: TeamsRepository,
    forumSocket: TeamForumSocketManager,
    tokenStore: TokenStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val messages = remember { mutableStateListOf<TeamForumMessageDto>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var typingHint by remember { mutableStateOf<String?>(null) }
    val sortedMessages by remember {
        derivedStateOf { messages.sortedBy { it.createdAt } }
    }

    var menuMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var editMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var editBody by remember { mutableStateOf("") }
    var editBusy by remember { mutableStateOf(false) }

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
                deletedAt = ev.deletedAt ?: messages[i].deletedAt,
                deletedByUserId = ev.deletedByUserId ?: messages[i].deletedByUserId,
            )
        }
    }

    fun mergeNew(msg: TeamForumMessageDto) {
        if (messages.any { it.id == msg.id }) return
        messages.add(msg)
    }

    LaunchedEffect(teamId, topicId) {
        loading = true
        error = null
        messages.clear()
        teamsRepository.listForumMessages(teamId, topicId, before = null, limit = 50)
            .onSuccess { page -> messages.addAll(page) }
            .onFailure { e -> error = e.toUserMessageRu(res) }
        loading = false
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        topicTitle.ifBlank { stringResource(R.string.team_forum_topic_fallback) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .imePadding(),
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
            HorizontalDivider()
            Box(Modifier.weight(1f)) {
                if (loading && messages.isEmpty()) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        strokeWidth = 2.dp,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sortedMessages, key = { it.id }) { msg ->
                            val mine = msg.senderUserId == currentUserId
                            val canEdit = (mine || canModerateMessages) && msg.deletedAt.isNullOrBlank()
                            ForumMessageBubble(
                                message = msg,
                                isMine = mine,
                                canEdit = canEdit,
                                onLongPressMenu = { if (canEdit) menuMessage = msg },
                            )
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        if (it.isNotEmpty()) {
                            forumSocket.emitTyping()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.team_forum_message_hint)) },
                    maxLines = 5,
                    enabled = !sending,
                )
                TextButton(
                    enabled = !sending && draft.trim().isNotEmpty(),
                    onClick = {
                        val t = draft.trim()
                        if (t.isEmpty()) return@TextButton
                        scope.launch {
                            sending = true
                            teamsRepository.postForumMessage(teamId, topicId, t)
                                .onSuccess { mergeNew(it); draft = "" }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            sending = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.chat_send))
                }
            }
        }
    }

    menuMessage?.let { msg ->
        val canEditMsg = (msg.senderUserId == currentUserId || canModerateMessages) &&
            msg.deletedAt.isNullOrBlank()
        AlertDialog(
            onDismissRequest = { menuMessage = null },
            title = { Text(stringResource(R.string.team_forum_message_actions_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        msg.text.take(280) + if (msg.text.length > 280) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (canEditMsg) {
                        TextButton(
                            onClick = {
                                editMessage = msg
                                editBody = msg.text
                                menuMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.team_forum_edit_message))
                        }
                        TextButton(
                            onClick = {
                                menuMessage = null
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
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.team_forum_delete_message),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuMessage = null }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    editMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { if (!editBusy) editMessage = null },
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
                    enabled = !editBusy && editBody.trim().isNotEmpty(),
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumMessageBubble(
    message: TeamForumMessageDto,
    isMine: Boolean,
    canEdit: Boolean,
    onLongPressMenu: () -> Unit,
) {
    val deleted = !message.deletedAt.isNullOrBlank()
    val container = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = if (canEdit) {
                    { onLongPressMenu() }
                } else {
                    null
                },
            ),
        color = container,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isMine) stringResource(R.string.team_forum_you) else message.senderUsername,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!message.editedAt.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.chat_edited),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Text(
                        formatForumTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (deleted) stringResource(R.string.team_forum_message_deleted) else message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (deleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
