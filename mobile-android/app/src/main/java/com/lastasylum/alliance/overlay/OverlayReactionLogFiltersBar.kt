package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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

private val FilterFieldHeight = 40.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        selectedLabelColor = MaterialTheme.colorScheme.primary,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OverlayReactionLogFilter.entries.forEach { option ->
                FilterChip(
                    selected = directionFilter == option,
                    onClick = { onDirectionFilter(option) },
                    label = {
                        Text(
                            text = when (option) {
                                OverlayReactionLogFilter.All ->
                                    stringResource(R.string.overlay_notifications_filter_all)
                                OverlayReactionLogFilter.Incoming ->
                                    stringResource(R.string.overlay_notifications_filter_incoming)
                                OverlayReactionLogFilter.Outgoing ->
                                    stringResource(R.string.overlay_notifications_filter_outgoing)
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = chipColors,
                )
            }
            ScopeFilterDropdown(
                selected = scopeFilter,
                onSelected = onScopeFilter,
                fieldColors = fieldColors,
                fieldShape = fieldShape,
            )
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .height(FilterFieldHeight),
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.overlay_notifications_search_hint),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            colors = fieldColors,
            shape = fieldShape,
            textStyle = MaterialTheme.typography.labelSmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeFilterDropdown(
    selected: OverlayReactionLogScopeFilter,
    onSelected: (OverlayReactionLogScopeFilter) -> Unit,
    fieldColors: androidx.compose.material3.TextFieldColors,
    fieldShape: RoundedCornerShape,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        OverlayReactionLogScopeFilter.All -> stringResource(R.string.overlay_notifications_scope_all)
        OverlayReactionLogScopeFilter.Personal ->
            stringResource(R.string.overlay_reaction_burst_caption_private)
        OverlayReactionLogScopeFilter.Broadcast ->
            stringResource(R.string.overlay_reaction_burst_caption_broadcast)
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .height(FilterFieldHeight),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = fieldColors,
            shape = fieldShape,
            textStyle = MaterialTheme.typography.labelSmall,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}
