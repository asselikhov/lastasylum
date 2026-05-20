package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun OverlayHudPanelHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SquadRelayDimens.contentPaddingHorizontal,
                end = 4.dp,
                top = 2.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (onRefresh != null) {
            IconButton(
                onClick = onRefresh,
                enabled = !refreshing,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.overlay_online_refresh_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.overlay_history_close_cd),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
