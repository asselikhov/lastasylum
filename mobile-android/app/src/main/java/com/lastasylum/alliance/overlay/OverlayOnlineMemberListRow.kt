package com.lastasylum.alliance.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.util.formatOverlayPresenceAgeRu

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayOnlineMemberListRow(
    member: OverlayOnlineMemberUiModel,
    selfLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = OverlayOnlineMemberTokens
    val roleColor = roleAccentColor(member.teamRole)
    val displayName = if (member.isSelf) {
        "${member.username} ($selfLabel)"
    } else {
        member.username
    }
    val presenceAge = formatOverlayPresenceAgeRu(member.lastPresenceAt)
    val statusText = stringResource(R.string.overlay_online_status_recent)
    val subtitle = if (presenceAge.isNotBlank()) presenceAge else statusText
    val a11y = stringResource(
        R.string.overlay_online_member_a11y,
        displayName,
        member.teamRole,
        statusText,
        if (presenceAge.isNotBlank()) ", $presenceAge" else "",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.glassFill)
            .border(1.dp, tokens.borderRecent, RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = a11y }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OverlayOnlineMemberAvatar(
            username = member.username,
            avatarRelativeUrl = member.avatarRelativeUrl,
            inGameNow = false,
            freshness = PresenceFreshness.Stale,
            outerSize = tokens.avatarOuter * 0.85f,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = tokens.titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = tokens.metaStyle.copy(color = tokens.mutedColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = member.teamRole,
            style = tokens.chipStyle.copy(color = roleColor),
            modifier = Modifier
                .clip(RoundedCornerShape(tokens.chipRadius))
                .background(roleColor.copy(alpha = 0.14f))
                .border(0.5.dp, roleColor.copy(alpha = 0.35f), RoundedCornerShape(tokens.chipRadius))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

