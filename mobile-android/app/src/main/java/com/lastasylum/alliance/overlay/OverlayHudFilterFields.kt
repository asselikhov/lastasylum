package com.lastasylum.alliance.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.components.CompactSearchBar

/** Shared filter + search styling in overlay «Уведомления» and «Участники онлайн». */
object OverlayHudFilterFields {
    val SectionVerticalPadding = 6.dp
    val FilterSearchRowSpacing = 8.dp
    val FilterDropdownMinWidth = 108.dp
    val FilterDropdownMaxWidth = 132.dp
    val FieldHeight = 40.dp
    val FieldCorner = RoundedCornerShape(12.dp)

    @Composable
    fun fieldColors() = Pair(
        MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    )
}

@Composable
fun <T> OverlayHudCompactFilterDropdown(
    selected: T,
    options: List<T>,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = labelFor(selected)
    val filterCd = stringResource(R.string.overlay_hud_filter_cd)
    val (surfaceColor, border) = OverlayHudFilterFields.fieldColors()

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(OverlayHudFilterFields.FieldHeight)
                .semantics { contentDescription = filterCd }
                .clickable { expanded = true },
            shape = OverlayHudFilterFields.FieldCorner,
            color = surfaceColor,
            border = border,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = labelFor(option),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (option != selected) onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
fun <T> OverlayHudFilterSearchRow(
    selectedFilter: T,
    filterOptions: List<T>,
    filterLabelFor: @Composable (T) -> String,
    onFilterSelect: (T) -> Unit,
    searchQuery: String,
    onSearchQuery: (String) -> Unit,
    searchHint: String,
    searchClearContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OverlayHudFilterFields.FilterSearchRowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayHudCompactFilterDropdown(
            selected = selectedFilter,
            options = filterOptions,
            labelFor = filterLabelFor,
            onSelect = onFilterSelect,
            modifier = Modifier.widthIn(
                min = OverlayHudFilterFields.FilterDropdownMinWidth,
                max = OverlayHudFilterFields.FilterDropdownMaxWidth,
            ),
        )
        CompactSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQuery,
            hint = searchHint,
            clearContentDescription = searchClearContentDescription,
            modifier = Modifier.weight(1f),
        )
    }
}
