package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared filter + search styling in overlay «Уведомления» and «Участники онлайн». */
object OverlayHudFilterFields {
    val SectionVerticalSpacing = 8.dp
    val FilterChipSpacing = 6.dp
    val SectionVerticalPadding = 6.dp

    @Composable
    fun filterChipColors() = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        selectedLabelColor = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun OverlayHudFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = OverlayHudFilterFields.filterChipColors(),
    )
}

@Composable
fun OverlayHudFilterChipRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OverlayHudFilterFields.FilterChipSpacing),
    ) {
        content()
    }
}
