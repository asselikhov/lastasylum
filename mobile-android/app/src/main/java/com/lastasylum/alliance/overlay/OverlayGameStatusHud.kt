package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R

data class OverlayGameStatusHudState(
    val ingameOverlayCount: Int = 0,
    val allianceChatUnread: Int = 0,
    val teamNewsUnread: Int = 0,
)

@Composable
fun OverlayGameStatusHud(
    state: OverlayGameStatusHudState,
    modifier: Modifier = Modifier,
) {
    val chips = buildList {
        if (state.ingameOverlayCount > 0) {
            add(
                HudChipKind.Online to state.ingameOverlayCount,
            )
        }
        if (state.allianceChatUnread > 0) {
            add(
                HudChipKind.AllianceChat to state.allianceChatUnread,
            )
        }
        if (state.teamNewsUnread > 0) {
            add(
                HudChipKind.News to state.teamNewsUnread,
            )
        }
    }
    if (chips.isEmpty()) return

    Row(
        modifier = modifier
            .background(
                color = Color(0xCC10141E),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { (kind, count) ->
            OverlayGameStatusHudChip(kind = kind, count = count)
        }
    }
}

private enum class HudChipKind {
    Online,
    AllianceChat,
    News,
}

@Composable
private fun OverlayGameStatusHudChip(
    kind: HudChipKind,
    count: Int,
) {
    val label = when (kind) {
        HudChipKind.Online -> stringResource(R.string.overlay_hud_online_cd, count)
        HudChipKind.AllianceChat -> stringResource(R.string.overlay_hud_alliance_chat_cd, count)
        HudChipKind.News -> stringResource(R.string.overlay_hud_news_cd, count)
    }
    val icon = when (kind) {
        HudChipKind.Online -> Icons.Outlined.Groups
        HudChipKind.AllianceChat -> Icons.Outlined.Shield
        HudChipKind.News -> Icons.AutoMirrored.Outlined.Article
    }
    val tint = when (kind) {
        HudChipKind.Online -> Color(0xFF81C784)
        HudChipKind.AllianceChat -> Color(0xFF4DB6AC)
        HudChipKind.News -> Color(0xFF90CAF9)
    }
    Row(
        modifier = Modifier
            .background(
                color = Color(0x661A1F2B),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color(0xFFEAF0FF),
            fontSize = 11.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
