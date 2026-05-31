package com.lastasylum.alliance.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.admin.StickerAllianceMemberDto
import com.lastasylum.alliance.data.admin.StickerPackCatalogItemDto
import com.lastasylum.alliance.data.auth.AccountRoles
import com.lastasylum.alliance.ui.admin.AdminUiState
import com.lastasylum.alliance.ui.util.accountRoleLabel
import com.lastasylum.alliance.ui.util.formatServerLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminStickerAccessSheet(
    state: AdminUiState,
    onSelectPack: (String) -> Unit,
    onMemberSearchChange: (String) -> Unit,
    onToggleRole: (String, String, Boolean) -> Unit,
    onToggleUser: (String, String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onClearError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val allianceCode = state.stickerAllianceCode.orEmpty()
    val selectedKey = state.stickerSelectedPackKey
        ?: state.stickerCatalog.firstOrNull()?.key
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.admin_sticker_section_title) + " · $allianceCode",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.admin_sticker_roles_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.stickerAccessLoading && state.stickerCatalog.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
        state.stickerAccessError?.let { err ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearError) {
                    Text(stringResource(R.string.admin_dismiss_error))
                }
            }
        }
        if (state.stickerCatalog.isNotEmpty()) {
            PrimaryScrollableTabRow(
                selectedTabIndex = state.stickerCatalog.indexOfFirst { it.key == selectedKey }
                    .coerceAtLeast(0),
            ) {
                state.stickerCatalog.forEach { item ->
                    val selected = item.key == selectedKey
                    Tab(
                        selected = selected,
                        onClick = { onSelectPack(item.key) },
                        text = { Text(item.title.ifBlank { item.key }) },
                    )
                }
            }
            selectedKey?.let { packKey ->
                Text(
                    stringResource(R.string.admin_sticker_roles_by_rank),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountRoles.ALL.forEach { role ->
                        FilterChip(
                            selected = state.stickerRoleGrants[packKey]?.contains(role) == true,
                            onClick = {
                                val on = state.stickerRoleGrants[packKey]?.contains(role) != true
                                onToggleRole(packKey, role, on)
                            },
                            label = { Text(accountRoleLabel(role)) },
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.admin_sticker_users_by_player),
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = state.stickerMemberSearchQuery,
                    onValueChange = onMemberSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.admin_sticker_member_search_hint)) },
                )
                val query = state.stickerMemberSearchQuery.trim().lowercase()
                val filtered = state.stickerMembers.filter { member ->
                    query.isEmpty() ||
                        member.username.lowercase().contains(query) ||
                        member.userId.lowercase().contains(query)
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(filtered, key = { it.userId }) { member ->
                        StickerMemberRow(
                            member = member,
                            checked = state.stickerUserGrants[packKey]?.contains(member.userId) == true,
                            onCheckedChange = { on ->
                                onToggleUser(packKey, member.userId, on)
                            },
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.admin_delete_cancel))
            }
            Button(
                onClick = onSave,
                enabled = !state.stickerAccessLoading && state.stickerCatalog.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.admin_sticker_save))
            }
        }
    }
}

@Composable
private fun StickerMemberRow(
    member: StickerAllianceMemberDto,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(member.username, style = MaterialTheme.typography.bodyMedium)
            val server = member.serverNumber?.let { formatServerLabel(it) }
            Text(
                buildString {
                    append(accountRoleLabel(member.accountRole))
                    if (server != null) append(" · ").append(server)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
