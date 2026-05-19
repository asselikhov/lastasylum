package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R

data class OverlayGameTopRightHudState(
    val ingameOverlayCount: Int = 0,
    val micOn: Boolean = false,
    val soundOn: Boolean = false,
    val voiceExpanded: Boolean = false,
)

@Composable
fun OverlayGameTopRightHud(
    state: OverlayGameTopRightHudState,
    onOnlineClick: () -> Unit,
    onQuickCommandsClick: () -> Unit,
    onVoiceHubClick: () -> Unit,
    onMicClick: () -> Unit,
    onSoundClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TopRightHudIcon(
            painter = painterResource(R.drawable.ic_overlay_online),
            badgeCount = state.ingameOverlayCount,
            active = state.ingameOverlayCount > 0,
            accent = Color(0xFF2E7D32),
            contentDescription = stringResource(R.string.overlay_hud_online_cd, state.ingameOverlayCount),
            onClick = onOnlineClick,
        )
        TopRightHudIcon(
            painter = painterResource(R.drawable.ic_overlay_history),
            badgeCount = 0,
            active = false,
            accent = Color(0xFF7E57C2),
            contentDescription = stringResource(R.string.overlay_cd_commands),
            onClick = onQuickCommandsClick,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TopRightHudIcon(
                painter = painterResource(R.drawable.ic_overlay_mic),
                badgeCount = 0,
                active = state.micOn || state.soundOn,
                accent = Color(0xFF5C6BC0),
                contentDescription = stringResource(
                    if (state.voiceExpanded) {
                        R.string.overlay_voice_hub_collapse_cd
                    } else {
                        R.string.overlay_voice_hub_cd
                    },
                ),
                onClick = onVoiceHubClick,
            )
            if (state.voiceExpanded) {
                TopRightHudIcon(
                    painter = painterResource(
                        if (state.micOn) R.drawable.ic_overlay_mic_on else R.drawable.ic_overlay_mic_off,
                    ),
                    badgeCount = 0,
                    active = state.micOn,
                    accent = Color(0xFF2E7D32),
                    contentDescription = stringResource(
                        if (state.micOn) R.string.overlay_voice_mic_on_cd else R.string.overlay_voice_mic_off_cd,
                    ),
                    onClick = onMicClick,
                )
                TopRightHudIcon(
                    painter = painterResource(
                        if (state.soundOn) R.drawable.ic_overlay_volume_on else R.drawable.ic_overlay_volume_off,
                    ),
                    badgeCount = 0,
                    active = state.soundOn,
                    accent = Color(0xFF1565C0),
                    contentDescription = stringResource(
                        if (state.soundOn) R.string.overlay_voice_sound_on_cd else R.string.overlay_voice_sound_off_cd,
                    ),
                    onClick = onSoundClick,
                )
            }
        }
    }
}

@Composable
private fun TopRightHudIcon(
    painter: Painter,
    badgeCount: Int,
    active: Boolean,
    accent: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val bg = if (active) accent else Color(0xCC37415C)
    val badge = badgeCount.coerceAtLeast(0)
    Box(
        modifier = Modifier
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bg, CircleShape)
                .clickable(onClick = onClick)
                .padding(8.dp),
        )
        if (badge > 0) {
            val badgeText = if (badge > 99) "99+" else badge.toString()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .background(Color(0xFFE53935), CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 9.sp,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}
