package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamJoinRequestDto
import com.lastasylum.alliance.data.teams.TeamSearchResultDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.telegramDisplayHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ProfileEditDialog { None, DisplayName, Team, Telegram }

private fun teamDisplayValue(profile: MyProfileDto): String {
    val name = profile.teamDisplayName?.trim().orEmpty()
    val tag = profile.teamTag?.trim().orEmpty()
    if (name.isNotEmpty() && tag.isNotEmpty()) return "$name [$tag]"
    if (name.isNotEmpty()) return name
    return profile.allianceName
}

private fun playerTeamShortLabel(p: MyProfileDto): String? {
    val tag = p.playerTeamTag?.trim()?.takeIf { it.isNotEmpty() }
    if (tag != null) return "[$tag]"
    val n = p.playerTeamDisplayName?.trim().orEmpty()
    if (n.length >= 3) return "[${n.take(3).uppercase()}]"
    if (n.isNotEmpty()) return "[$n]"
    return null
}

private fun isValidThreeLetterTeamTag(raw: String): Boolean {
    val t = raw.trim()
    var i = 0
    var count = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        if (!Character.isLetter(cp)) return false
        count++
        if (count > 3) return false
        i += Character.charCount(cp)
    }
    return count == 3
}

@Composable
private fun membershipLabel(status: String): String {
    return when (status.lowercase()) {
        "pending" -> stringResource(R.string.admin_status_pending)
        "active" -> stringResource(R.string.admin_status_active)
        "removed" -> stringResource(R.string.admin_status_removed)
        else -> status
    }
}

@Composable
private fun ProfileStatRow(
    label: String,
    value: String,
    subtitle: String? = null,
    editable: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (editable && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { s ->
                Text(
                    text = s,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (editable && onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ProfileScreen(
    username: String,
    onLogout: () -> Unit,
    onOpenTeam: (String) -> Unit,
    teamsRepository: TeamsRepository,
) {
    val context = LocalContext.current
    val app = remember { AppContainer.from(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var profile by remember { mutableStateOf<MyProfileDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var dialog by remember { mutableStateOf(ProfileEditDialog.None) }
    var draft by remember { mutableStateOf("") }
    var teamDraftName by remember { mutableStateOf("") }
    var teamDraftTag by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var dialogSaving by remember { mutableStateOf(false) }

    var showPlayerTeamCreate by remember { mutableStateOf(false) }
    var showPlayerTeamJoin by remember { mutableStateOf(false) }
    var showJoinInbox by remember { mutableStateOf(false) }
    var createPlayerTeamName by remember { mutableStateOf("") }
    var createPlayerTeamTag by remember { mutableStateOf("") }
    var createPlayerTeamError by remember { mutableStateOf<String?>(null) }
    var createPlayerTeamBusy by remember { mutableStateOf(false) }
    var joinSearch by remember { mutableStateOf("") }
    var joinResults by remember { mutableStateOf<List<TeamSearchResultDto>>(emptyList()) }
    var joinSearchBusy by remember { mutableStateOf(false) }
    var joinActionBusy by remember { mutableStateOf(false) }
    var joinFeedback by remember { mutableStateOf<String?>(null) }
    var inboxRequests by remember { mutableStateOf<List<TeamJoinRequestDto>>(emptyList()) }
    var inboxBusy by remember { mutableStateOf(false) }

    LaunchedEffect(joinSearch) {
        delay(400)
        val q = joinSearch.trim()
        if (q.length < 1) {
            joinResults = emptyList()
            return@LaunchedEffect
        }
        joinSearchBusy = true
        teamsRepository.searchTeams(q)
            .onSuccess { joinResults = it }
            .onFailure { joinResults = emptyList() }
        joinSearchBusy = false
    }

    LaunchedEffect(app) {
        app.usersRepository.getMyProfile()
            .onSuccess {
                profile = it
                loadError = null
            }
            .onFailure {
                loadError = context.getString(R.string.profile_load_error)
            }
    }

    val displayName = profile?.username ?: username
    val initialLetter = remember(displayName) {
        displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    fun openDialog(which: ProfileEditDialog, initialDraft: String) {
        dialog = which
        draft = initialDraft
        dialogError = null
    }

    fun closeDialog() {
        dialog = ProfileEditDialog.None
        dialogError = null
        dialogSaving = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(
                horizontal = SquadRelayDimens.contentPaddingHorizontal,
                vertical = SquadRelayDimens.screenTopPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.sectionGap),
    ) {
        Text(
            text = stringResource(R.string.profile_header_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SquadRelayDimens.panelInnerPadding)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = initialLetter,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        val avatarUrl = telegramAvatarUrl(profile?.telegramUsername)
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = stringResource(R.string.profile_telegram_avatar_cd),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        loadError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            profile?.let { p ->
                                val bracket = playerTeamShortLabel(p)
                                val pid = p.playerTeamId?.takeIf { it.isNotBlank() }
                                if (bracket != null) {
                                    Text(
                                        text = bracket,
                                        modifier = Modifier
                                            .then(
                                                if (pid != null) {
                                                    Modifier.clickable { onOpenTeam(pid) }
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .padding(end = 6.dp),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color(0xFF5DADE2),
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                    )
                                }
                            }
                            Text(
                                text = displayName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        val hasPlayerTeam = profile?.playerTeamId?.isNotBlank() == true
                        if (profile != null && !hasPlayerTeam) {
                            Text(
                                text = stringResource(R.string.profile_player_team_section),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        createPlayerTeamName = ""
                                        createPlayerTeamTag = ""
                                        createPlayerTeamError = null
                                        showPlayerTeamCreate = true
                                    },
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Text(stringResource(R.string.profile_player_team_create))
                                }
                                OutlinedButton(
                                    onClick = {
                                        joinSearch = ""
                                        joinResults = emptyList()
                                        joinFeedback = null
                                        showPlayerTeamJoin = true
                                    },
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Text(stringResource(R.string.profile_player_team_join))
                                }
                            }
                        }
                    }
                }
                val pending = profile?.pendingPlayerTeamJoinRequests ?: 0
                if (profile?.isPlayerTeamLeader == true && pending > 0) {
                    BadgedBox(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp),
                        badge = {
                            Badge {
                                Text(
                                    text = if (pending > 9) "9+" else "$pending",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                    ) {
                        IconButton(
                            onClick = {
                                showJoinInbox = true
                                scope.launch {
                                    inboxBusy = true
                                    teamsRepository.listPendingJoinRequests()
                                        .onSuccess { inboxRequests = it }
                                        .onFailure { inboxRequests = emptyList() }
                                    inboxBusy = false
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inbox,
                                contentDescription = stringResource(R.string.profile_join_inbox_cd),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.profile_section_account),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
                )
                profile?.let { p ->
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_ingame_name),
                        value = p.username,
                        editable = true,
                        onClick = { openDialog(ProfileEditDialog.DisplayName, p.username) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_email),
                        value = p.email,
                        editable = false,
                        onClick = null,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_role),
                        value = p.role,
                        editable = false,
                        onClick = null,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_membership),
                        value = membershipLabel(p.membershipStatus),
                        editable = false,
                        onClick = null,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_team),
                        value = teamDisplayValue(p),
                        subtitle = stringResource(R.string.profile_team_code_hint, p.allianceName),
                        editable = true,
                        onClick = {
                            teamDraftName = p.teamDisplayName?.trim().orEmpty()
                            teamDraftTag = p.teamTag?.trim().orEmpty()
                            dialog = ProfileEditDialog.Team
                            dialogError = null
                        },
                    )
                    p.alliancePublicId?.takeIf { it.isNotBlank() }?.let { teamPublicId ->
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                        ProfileStatRow(
                            label = stringResource(R.string.profile_team_public_id),
                            value = teamPublicId,
                            editable = false,
                            onClick = null,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_telegram),
                        value = telegramDisplayHandle(p.telegramUsername)
                            ?: stringResource(R.string.profile_value_not_set),
                        editable = true,
                        onClick = {
                            openDialog(
                                ProfileEditDialog.Telegram,
                                p.telegramUsername?.let { h -> "@$h" } ?: "",
                            )
                        },
                    )
                } ?: run {
                    Text(
                        text = stringResource(R.string.profile_load_error),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.padding(bottom = 8.dp))
            }
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(stringResource(R.string.profile_logout))
        }
    }

    if (showPlayerTeamCreate) {
        AlertDialog(
            onDismissRequest = { if (!createPlayerTeamBusy) showPlayerTeamCreate = false },
            title = { Text(stringResource(R.string.profile_player_team_create_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = createPlayerTeamName,
                        onValueChange = {
                            createPlayerTeamName = it.take(48)
                            createPlayerTeamError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_field_team_full_name)) },
                        singleLine = true,
                        enabled = !createPlayerTeamBusy,
                    )
                    OutlinedTextField(
                        value = createPlayerTeamTag,
                        onValueChange = { v ->
                            val letters = v.filter { it.isLetter() }
                            createPlayerTeamTag = letters.take(3)
                            createPlayerTeamError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_field_team_tag)) },
                        singleLine = true,
                        enabled = !createPlayerTeamBusy,
                    )
                    createPlayerTeamError?.let { e ->
                        Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val n = createPlayerTeamName.trim()
                        val tg = createPlayerTeamTag.trim()
                        if (n.length < 2 || !isValidThreeLetterTeamTag(tg)) return@Button
                        scope.launch {
                            createPlayerTeamBusy = true
                            createPlayerTeamError = null
                            teamsRepository.createTeam(n, tg)
                                .onSuccess { resp ->
                                    app.usersRepository.getMyProfile()
                                        .onSuccess { profile = it }
                                    showPlayerTeamCreate = false
                                    onOpenTeam(resp.teamId)
                                }
                                .onFailure {
                                    createPlayerTeamError =
                                        context.getString(R.string.profile_player_team_save_error)
                                }
                            createPlayerTeamBusy = false
                        }
                    },
                    enabled = !createPlayerTeamBusy &&
                        createPlayerTeamName.trim().length >= 2 &&
                        isValidThreeLetterTeamTag(createPlayerTeamTag),
                ) {
                    if (createPlayerTeamBusy) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.profile_action_save))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!createPlayerTeamBusy) showPlayerTeamCreate = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    if (showPlayerTeamJoin) {
        AlertDialog(
            onDismissRequest = { if (!joinActionBusy) showPlayerTeamJoin = false },
            title = { Text(stringResource(R.string.profile_player_team_join_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = joinSearch,
                        onValueChange = {
                            joinSearch = it
                            joinFeedback = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_player_team_join_hint)) },
                        singleLine = true,
                        enabled = !joinActionBusy,
                    )
                    if (joinSearchBusy) {
                        CircularProgressIndicator(
                            Modifier
                                .padding(8.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    joinFeedback?.let { f ->
                        Text(f, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(joinResults, key = { it.id }) { t ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "[${t.tag}] ${t.displayName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(
                                        enabled = !joinActionBusy,
                                        onClick = {
                                            scope.launch {
                                                joinActionBusy = true
                                                teamsRepository.submitJoinRequest(t.id)
                                                    .onSuccess {
                                                        joinFeedback = context.getString(
                                                            R.string.profile_player_team_join_sent,
                                                        )
                                                    }
                                                    .onFailure {
                                                        joinFeedback = context.getString(
                                                            R.string.profile_player_team_save_error,
                                                        )
                                                    }
                                                joinActionBusy = false
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.profile_player_team_join_submit))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (!joinActionBusy) showPlayerTeamJoin = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    if (showJoinInbox) {
        AlertDialog(
            onDismissRequest = { if (!inboxBusy) showJoinInbox = false },
            title = { Text(stringResource(R.string.profile_join_inbox_title)) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    if (inboxBusy && inboxRequests.isEmpty()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(inboxRequests, key = { it.id }) { r ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = r.requesterUsername,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                inboxBusy = true
                                                teamsRepository.acceptJoinRequest(r.id)
                                                    .onSuccess {
                                                        teamsRepository.listPendingJoinRequests()
                                                            .onSuccess { inboxRequests = it }
                                                        app.usersRepository.getMyProfile()
                                                            .onSuccess { profile = it }
                                                    }
                                                inboxBusy = false
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.profile_join_accept))
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                inboxBusy = true
                                                teamsRepository.rejectJoinRequest(r.id)
                                                    .onSuccess {
                                                        teamsRepository.listPendingJoinRequests()
                                                            .onSuccess { inboxRequests = it }
                                                        app.usersRepository.getMyProfile()
                                                            .onSuccess { profile = it }
                                                    }
                                                inboxBusy = false
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.profile_join_reject))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showJoinInbox = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
    }

    when (dialog) {
        ProfileEditDialog.DisplayName -> {
            AlertDialog(
                onDismissRequest = { if (!dialogSaving) closeDialog() },
                title = { Text(stringResource(R.string.profile_edit_name_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                dialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !dialogSaving,
                            supportingText = {
                                Text(stringResource(R.string.profile_hint_ingame_name))
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text,
                            ),
                        )
                        dialogError?.let { e ->
                            Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = draft.trim()
                            if (trimmed.length < 3) return@Button
                            scope.launch {
                                dialogSaving = true
                                dialogError = null
                                app.usersRepository.updateMyUsername(trimmed)
                                    .onSuccess {
                                        profile = it
                                        closeDialog()
                                    }
                                    .onFailure {
                                        dialogError = context.getString(R.string.profile_save_error_generic)
                                    }
                                dialogSaving = false
                            }
                        },
                        enabled = !dialogSaving && draft.trim().length >= 3,
                    ) {
                        if (dialogSaving) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.profile_action_save))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!dialogSaving) closeDialog() }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }

        ProfileEditDialog.Team -> {
            val canSaveTeam =
                (teamDraftName.isBlank() && teamDraftTag.isBlank()) ||
                    (teamDraftName.isNotBlank() && isValidThreeLetterTeamTag(teamDraftTag))
            AlertDialog(
                onDismissRequest = { if (!dialogSaving) closeDialog() },
                title = { Text(stringResource(R.string.profile_edit_team_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = teamDraftName,
                            onValueChange = {
                                teamDraftName = it.take(48)
                                dialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.profile_field_team_full_name)) },
                            singleLine = true,
                            enabled = !dialogSaving,
                            supportingText = {
                                Text(stringResource(R.string.profile_hint_team_full))
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text,
                            ),
                        )
                        OutlinedTextField(
                            value = teamDraftTag,
                            onValueChange = { v ->
                                val letters = v.filter { it.isLetter() }
                                teamDraftTag = letters.take(3)
                                dialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.profile_field_team_tag)) },
                            singleLine = true,
                            enabled = !dialogSaving,
                            supportingText = {
                                Text(stringResource(R.string.profile_hint_team_tag))
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Text,
                            ),
                        )
                        Text(
                            text = stringResource(R.string.profile_hint_team_clear),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        dialogError?.let { e ->
                            Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val n = teamDraftName.trim()
                            val tg = teamDraftTag.trim()
                            scope.launch {
                                dialogSaving = true
                                dialogError = null
                                app.usersRepository.updateMyTeamDisplay(n, tg)
                                    .onSuccess {
                                        profile = it
                                        closeDialog()
                                    }
                                    .onFailure {
                                        dialogError = context.getString(R.string.profile_save_error_generic)
                                    }
                                dialogSaving = false
                            }
                        },
                        enabled = !dialogSaving && canSaveTeam,
                    ) {
                        if (dialogSaving) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.profile_action_save))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!dialogSaving) closeDialog() }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }

        ProfileEditDialog.Telegram -> {
            AlertDialog(
                onDismissRequest = { if (!dialogSaving) closeDialog() },
                title = { Text(stringResource(R.string.profile_edit_telegram_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                dialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !dialogSaving,
                            supportingText = {
                                Text(stringResource(R.string.profile_hint_telegram))
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                            ),
                        )
                        dialogError?.let { e ->
                            Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !dialogSaving && profile?.telegramUsername != null,
                            onClick = {
                                scope.launch {
                                    dialogSaving = true
                                    dialogError = null
                                    app.usersRepository.updateMyTelegram("")
                                        .onSuccess {
                                            profile = it
                                            closeDialog()
                                        }
                                        .onFailure {
                                            dialogError =
                                                context.getString(R.string.profile_save_error_telegram)
                                        }
                                    dialogSaving = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.profile_action_clear_link))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    dialogSaving = true
                                    dialogError = null
                                    val raw = draft.trim().removePrefix("@").trim()
                                    app.usersRepository.updateMyTelegram(raw)
                                        .onSuccess {
                                            profile = it
                                            closeDialog()
                                        }
                                        .onFailure {
                                            dialogError =
                                                context.getString(R.string.profile_save_error_telegram)
                                        }
                                    dialogSaving = false
                                }
                            },
                            enabled = !dialogSaving,
                        ) {
                            if (dialogSaving) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(stringResource(R.string.profile_action_save))
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!dialogSaving) closeDialog() }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }

        ProfileEditDialog.None -> Unit
    }
}
