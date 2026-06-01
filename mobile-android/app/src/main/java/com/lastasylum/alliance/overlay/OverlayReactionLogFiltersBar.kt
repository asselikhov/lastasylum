package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

private val FilterFieldMinHeight = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayReactionLogFiltersBar(
    directionFilter: OverlayReactionLogFilter,
    onDirectionFilter: (OverlayReactionLogFilter) -> Unit,
    scopeFilter: OverlayReactionLogScopeFilter,
    onScopeFilter: (OverlayReactionLogScopeFilter) -> Unit,
    searchQuery: String,
    onSearchQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color(0xFF3A4555),
        focusedContainerColor = Color(0xFF1A2836),
        unfocusedContainerColor = Color(0xFF141C28),
    )
    val fieldShape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        DirectionFilterDropdown(
            selected = directionFilter,
            onSelected = onDirectionFilter,
            fieldColors = fieldColors,
            fieldShape = fieldShape,
            modifier = Modifier.weight(0.95f),
        )
        ScopeFilterDropdown(
            selected = scopeFilter,
            onSelected = onScopeFilter,
            fieldColors = fieldColors,
            fieldShape = fieldShape,
            modifier = Modifier.weight(0.95f),
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQuery,
            modifier = Modifier
                .weight(1.1f)
                .heightIn(min = FilterFieldMinHeight),
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.overlay_notifications_search_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            colors = fieldColors,
            shape = fieldShape,
            textStyle = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionFilterDropdown(
    selected: OverlayReactionLogFilter,
    onSelected: (OverlayReactionLogFilter) -> Unit,
    fieldColors: androidx.compose.material3.TextFieldColors,
    fieldShape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val label = when (selected) {
        OverlayReactionLogFilter.All -> stringResource(R.string.overlay_notifications_filter_all)
        OverlayReactionLogFilter.Incoming -> stringResource(R.string.overlay_notifications_filter_incoming)
        OverlayReactionLogFilter.Outgoing -> stringResource(R.string.overlay_notifications_filter_outgoing)
        OverlayReactionLogFilter.Reply -> stringResource(R.string.overlay_notifications_filter_reply)
    }
    FilterDropdown(
        label = label,
        modifier = modifier,
        fieldColors = fieldColors,
        fieldShape = fieldShape,
    ) { onDismiss ->
        OverlayReactionLogFilter.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        when (option) {
                            OverlayReactionLogFilter.All ->
                                stringResource(R.string.overlay_notifications_filter_all)
                            OverlayReactionLogFilter.Incoming ->
                                stringResource(R.string.overlay_notifications_filter_incoming)
                            OverlayReactionLogFilter.Outgoing ->
                                stringResource(R.string.overlay_notifications_filter_outgoing)
                            OverlayReactionLogFilter.Reply ->
                                stringResource(R.string.overlay_notifications_filter_reply)
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                onClick = {
                    onDismiss()
                    onSelected(option)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeFilterDropdown(
    selected: OverlayReactionLogScopeFilter,
    onSelected: (OverlayReactionLogScopeFilter) -> Unit,
    fieldColors: androidx.compose.material3.TextFieldColors,
    fieldShape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val label = when (selected) {
        OverlayReactionLogScopeFilter.All -> stringResource(R.string.overlay_notifications_scope_all)
        OverlayReactionLogScopeFilter.Personal ->
            stringResource(R.string.overlay_reaction_burst_caption_private)
        OverlayReactionLogScopeFilter.Broadcast ->
            stringResource(R.string.overlay_reaction_burst_caption_broadcast)
    }
    FilterDropdown(
        label = label,
        modifier = modifier,
        fieldColors = fieldColors,
        fieldShape = fieldShape,
    ) { onDismiss ->
        OverlayReactionLogScopeFilter.entries.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        when (option) {
                            OverlayReactionLogScopeFilter.All ->
                                stringResource(R.string.overlay_notifications_scope_all)
                            OverlayReactionLogScopeFilter.Personal ->
                                stringResource(R.string.overlay_reaction_burst_caption_private)
                            OverlayReactionLogScopeFilter.Broadcast ->
                                stringResource(R.string.overlay_reaction_burst_caption_broadcast)
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                onClick = {
                    onDismiss()
                    onSelected(option)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    fieldColors: androidx.compose.material3.TextFieldColors,
    fieldShape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    menuItems: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .heightIn(min = FilterFieldMinHeight)
                .fillMaxWidth(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = fieldColors,
            shape = fieldShape,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            menuItems { expanded = false }
        }
    }
}
