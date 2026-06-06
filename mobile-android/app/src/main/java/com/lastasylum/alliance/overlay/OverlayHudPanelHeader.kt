package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun overlayPanelUnreadSubtitle(unreadCount: Int): String? {
    if (unreadCount <= 0) return null
    return if (unreadCount > OverlayBadgeFormat.CAP) {
        stringResource(R.string.overlay_panel_unread_subtitle_max)
    } else {
        stringResource(R.string.overlay_panel_unread_subtitle, unreadCount)
    }
}

@Composable
fun OverlayHudPanelHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleTrailing: @Composable (RowScope.() -> Unit)? = null,
    titleTrailing: @Composable (RowScope.() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onMarkAllRead: (() -> Unit)? = null,
    markAllReadEnabled: Boolean = true,
    markAllReadLoading: Boolean = false,
    markAllReadIconTint: Color = MaterialTheme.colorScheme.onSurface,
    headerTrailing: @Composable (RowScope.() -> Unit)? = null,
    closeIconTint: Color = MaterialTheme.colorScheme.onSurface,
    showCloseButton: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(12f)
            .padding(
                start = SquadRelayDimens.contentPaddingHorizontal,
                end = 4.dp,
                top = 2.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            titleTrailing?.invoke(this)
            if (onMarkAllRead != null) {
                OverlayMarkAsReadIconButton(
                    onClick = onMarkAllRead,
                    enabled = markAllReadEnabled,
                    loading = markAllReadLoading,
                    tint = markAllReadIconTint,
                )
            }
            headerTrailing?.invoke(this)
            if (headerTrailing == null && onRefresh != null && subtitleTrailing == null) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.overlay_notifications_refresh_cd),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (showCloseButton) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.overlay_history_close_cd),
                        tint = closeIconTint,
                    )
                }
            }
        }
        val showSubtitleRow = !subtitle.isNullOrBlank() || subtitleTrailing != null
        if (showSubtitleRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                subtitleTrailing?.invoke(this)
            }
        }
    }
}
