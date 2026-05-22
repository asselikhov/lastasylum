package com.lastasylum.alliance.ui.chat

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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

/**
 * Telegram-style control: jump to the newest messages when the user scrolled up in a reverse chat list.
 */
@Composable
fun ChatScrollToLatestFab(
    visible: Boolean,
    newMessageCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cd = if (newMessageCount > 0) {
        stringResource(R.string.chat_scroll_to_latest_cd_with_count, newMessageCount.coerceAtMost(99))
    } else {
        stringResource(R.string.chat_scroll_to_latest_cd)
    }
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
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.semantics {
                role = Role.Button
                contentDescription = cd
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
                if (newMessageCount > 0) {
                    Text(
                        text = if (newMessageCount > 99) {
                            stringResource(R.string.chat_scroll_to_latest_badge_max)
                        } else {
                            stringResource(R.string.chat_scroll_to_latest_badge, newMessageCount)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
