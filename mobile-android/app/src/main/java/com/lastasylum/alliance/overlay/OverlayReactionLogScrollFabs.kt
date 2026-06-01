package com.lastasylum.alliance.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import com.lastasylum.alliance.ui.components.premium.PremiumGlassBar
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

@Composable
fun OverlayReactionLogJumpToUnreadFab(
    visible: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (unreadCount <= 0) return
    val cd = stringResource(
        R.string.overlay_notifications_jump_unread_cd,
        unreadCount.coerceAtMost(99),
    )
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(140)) +
            scaleIn(initialScale = 0.88f, animationSpec = tween(180)) +
            slideInVertically(initialOffsetY = { -it / 3 }, animationSpec = tween(200)),
        exit = fadeOut(tween(100)) +
            scaleOut(targetScale = 0.92f, animationSpec = tween(120)) +
            slideOutVertically(targetOffsetY = { -it / 4 }, animationSpec = tween(140)),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, PremiumSurfaces.borderColor(0.14f)),
            modifier = Modifier
                .semantics {
                    role = Role.Button
                    contentDescription = cd
                }
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandLess,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = if (unreadCount > 99) {
                        stringResource(R.string.overlay_notifications_jump_unread_badge_max)
                    } else {
                        stringResource(R.string.overlay_notifications_jump_unread_badge, unreadCount)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun OverlayReactionLogScrollToLatestFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cd = stringResource(R.string.overlay_notifications_scroll_latest_cd)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(140)) +
            scaleIn(initialScale = 0.88f, animationSpec = tween(180)) +
            slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(200)),
        exit = fadeOut(tween(100)) +
            scaleOut(targetScale = 0.92f, animationSpec = tween(120)) +
            slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = tween(140)),
    ) {
        PremiumGlassBar(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .semantics {
                    role = Role.Button
                    contentDescription = cd
                }
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.overlay_notifications_scroll_latest),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
