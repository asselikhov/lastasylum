package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

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
    val fieldColors = OverlayHudFilterFields.notificationsFieldColors()
    val fieldShape = OverlayHudFilterFields.FieldShape
    val fieldTextStyle = OverlayHudFilterFields.textStyle()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(OverlayHudFilterFields.FieldSpacing),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        DirectionFilterDropdown(
            selected = directionFilter,
            onSelected = onDirectionFilter,
            fieldColors = fieldColors,
            fieldShape = fieldShape,
            fieldTextStyle = fieldTextStyle,
            modifier = Modifier
                .weight(0.95f)
                .then(OverlayHudFilterFields.baseFieldModifier()),
        )
        ScopeFilterDropdown(
            selected = scopeFilter,
            onSelected = onScopeFilter,
            fieldColors = fieldColors,
            fieldShape = fieldShape,
            fieldTextStyle = fieldTextStyle,
            modifier = Modifier
                .weight(0.95f)
                .then(OverlayHudFilterFields.baseFieldModifier()),
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQuery,
            modifier = Modifier
                .weight(1.1f)
                .then(OverlayHudFilterFields.baseFieldModifier()),
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            prefix = { Spacer(modifier = Modifier.width(2.dp)) },
            suffix = { Spacer(modifier = Modifier.width(4.dp)) },
            colors = fieldColors,
            shape = fieldShape,
            textStyle = fieldTextStyle,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionFilterDropdown(
    selected: OverlayReactionLogFilter,
    onSelected: (OverlayReactionLogFilter) -> Unit,
    fieldColors: androidx.compose.material3.TextFieldColors,
    fieldShape: androidx.compose.foundation.shape.RoundedCornerShape,
    fieldTextStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val label = when (selected) {
        OverlayReactionLogFilter.All -> stringResource(R.string.overlay_notifications_filter_all)
        OverlayReactionLogFilter.Incoming -> stringResource(R.string.overlay_notifications_filter_incoming)
        OverlayReactionLogFilter.Outgoing -> stringResource(R.string.overlay_notifications_filter_outgoing)
    }
    FilterDropdown(
        label = label,
        modifier = modifier,
        fieldColors = fieldColors,
        fieldShape = fieldShape,
        fieldTextStyle = fieldTextStyle,
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
                        },
                        style = OverlayHudFilterFields.menuItemTextStyle(),
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
    fieldShape: androidx.compose.foundation.shape.RoundedCornerShape,
    fieldTextStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val label = when (selected) {
        OverlayReactionLogScopeFilter.All -> stringResource(R.string.overlay_notifications_scope_all)
        OverlayReactionLogScopeFilter.Personal ->
            stringResource(R.string.overlay_reaction_burst_caption_private)
        OverlayReactionLogScopeFilter.Broadcast ->
            stringResource(R.string.overlay_reaction_burst_caption_broadcast)
        OverlayReactionLogScopeFilter.Reply ->
            stringResource(R.string.overlay_notifications_reply_scope)
    }
    FilterDropdown(
        label = label,
        modifier = modifier,
        fieldColors = fieldColors,
        fieldShape = fieldShape,
        fieldTextStyle = fieldTextStyle,
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
                            OverlayReactionLogScopeFilter.Reply ->
                                stringResource(R.string.overlay_notifications_reply_scope)
                        },
                        style = OverlayHudFilterFields.menuItemTextStyle(),
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
    fieldShape: androidx.compose.foundation.shape.RoundedCornerShape,
    fieldTextStyle: androidx.compose.ui.text.TextStyle,
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
                .fillMaxWidth()
                .heightIn(min = OverlayHudFilterFields.FieldHeight)
                .defaultMinSize(minHeight = OverlayHudFilterFields.FieldHeight),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            prefix = { Spacer(modifier = Modifier.width(2.dp)) },
            suffix = { Spacer(modifier = Modifier.width(2.dp)) },
            colors = fieldColors,
            shape = fieldShape,
            textStyle = fieldTextStyle,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            menuItems { expanded = false }
        }
    }
}
