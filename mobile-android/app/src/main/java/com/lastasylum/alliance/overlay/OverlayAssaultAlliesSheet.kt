package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val OverlaySheetBgTop = Color(0xF2141C2A)
private val OverlaySheetBgBottom = Color(0xEE0C1018)
private val OverlaySheetStroke = Color(0x3D4A62AA)
private val OverlayMuted = Color(0xFF9AB0C4D8)
private val OverlayCyan = Color(0xFF38BDF8)

internal suspend fun loadOverlayAssaultTeamMembers(context: Context): List<PlayerTeamMemberDto> =
    withContext(Dispatchers.IO) {
        val team = OverlayTeamContextCache.peekCachedTeam()
        if (team != null) {
            return@withContext team.members.sortedBy { it.username.lowercase() }
        }
        val container = AppContainer.from(context)
        val uid = OverlayTeamContextCache.peekForPanel()?.currentUserId?.trim().orEmpty()
            .ifEmpty {
                container.usersRepository.peekMyProfile()?.id?.trim().orEmpty()
            }
        if (uid.isEmpty()) return@withContext emptyList()
        OverlayTeamContextCache.hydrateFromDisk(uid, container.usersRepository, container.launchDiskCache)
        OverlayTeamContextCache.peekCachedTeam()?.members?.sortedBy { it.username.lowercase() }
            ?: emptyList()
    }

@Composable
fun OverlayAssaultAlliesSheet(
    initialSelectedUserIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var members by remember { mutableStateOf<List<PlayerTeamMemberDto>>(emptyList()) }
    var selectedIds by remember(initialSelectedUserIds) {
        mutableStateOf(initialSelectedUserIds)
    }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        members = loadOverlayAssaultTeamMembers(context)
        loading = false
    }

    val filteredMembers by remember(members, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isEmpty()) members else members.filter { it.username.contains(q, ignoreCase = true) }
        }
    }

    val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OverlaySheetStroke, RoundedCornerShape(16.dp)),
        color = Color.Transparent,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(OverlaySheetBgTop, OverlaySheetBgBottom)),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.overlay_assault_allies_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = OverlayMuted)
                    }
                }
                Text(
                    text = stringResource(R.string.overlay_assault_allies_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = OverlayMuted,
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    placeholder = { Text(stringResource(R.string.overlay_assault_allies_search)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = OverlayMuted)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = OverlayCyan,
                        focusedBorderColor = OverlayCyan,
                        unfocusedBorderColor = OverlaySheetStroke,
                    ),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = OverlaySheetStroke,
                )
                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = OverlayCyan, modifier = Modifier.size(28.dp))
                        }
                    }
                    members.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.overlay_assault_allies_empty),
                            color = OverlayMuted,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                    else -> {
                        val allUserIds = remember(members) { members.map { it.userId }.toSet() }
                        val allSelected = allUserIds.isNotEmpty() && selectedIds.containsAll(allUserIds)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        selectedIds = if (allSelected) emptySet() else allUserIds
                                    },
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { on ->
                                    selectedIds = if (on) allUserIds else emptySet()
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = OverlayCyan,
                                    uncheckedColor = OverlayMuted,
                                    checkmarkColor = Color.Black,
                                ),
                            )
                            Text(
                                text = stringResource(R.string.overlay_assault_allies_select_all),
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(
                                    R.string.overlay_assault_allies_selected_count,
                                    selectedIds.count { allUserIds.contains(it) },
                                    allUserIds.size,
                                ),
                                color = OverlayMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                            color = OverlaySheetStroke,
                        )
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filteredMembers, key = { it.userId }) { member ->
                                val checked = selectedIds.contains(member.userId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                selectedIds = if (checked) {
                                                    selectedIds - member.userId
                                                } else {
                                                    selectedIds + member.userId
                                                }
                                            },
                                        )
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { on ->
                                            selectedIds = if (on) selectedIds + member.userId
                                            else selectedIds - member.userId
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = OverlayCyan,
                                            uncheckedColor = OverlayMuted,
                                            checkmarkColor = Color.Black,
                                        ),
                                    )
                                    Text(
                                        text = member.username,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { onConfirm(selectedIds) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OverlayCyan),
                ) {
                    Text(
                        text = if (selectedIds.isEmpty()) {
                            stringResource(R.string.overlay_assault_allies_confirm_all)
                        } else {
                            stringResource(R.string.overlay_assault_allies_confirm_count, selectedIds.size)
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
