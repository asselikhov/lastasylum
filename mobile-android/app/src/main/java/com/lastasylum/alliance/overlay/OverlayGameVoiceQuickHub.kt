package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

data class OverlayGameVoiceQuickHubState(
    val micOn: Boolean = false,
    val soundOn: Boolean = false,
    val expanded: Boolean = false,
)

@Composable
fun OverlayGameVoiceQuickHub(
    state: OverlayGameVoiceQuickHubState,
    onHubClick: () -> Unit,
    onMicClick: () -> Unit,
    onSoundClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VoiceQuickToggle(
            iconRes = R.drawable.ic_overlay_mic,
            active = state.micOn || state.soundOn,
            accent = Color(0xFF5C6BC0),
            contentDescription = stringResource(
                if (state.expanded) {
                    R.string.overlay_voice_hub_collapse_cd
                } else {
                    R.string.overlay_voice_hub_cd
                },
            ),
            onClick = onHubClick,
        )
        if (state.expanded) {
            VoiceQuickToggle(
                iconRes = if (state.micOn) {
                    R.drawable.ic_overlay_mic_on
                } else {
                    R.drawable.ic_overlay_mic_off
                },
                active = state.micOn,
                accent = Color(0xFF2E7D32),
                contentDescription = stringResource(
                    if (state.micOn) {
                        R.string.overlay_voice_mic_on_cd
                    } else {
                        R.string.overlay_voice_mic_off_cd
                    },
                ),
                onClick = onMicClick,
            )
            VoiceQuickToggle(
                iconRes = if (state.soundOn) {
                    R.drawable.ic_overlay_volume_on
                } else {
                    R.drawable.ic_overlay_volume_off
                },
                active = state.soundOn,
                accent = Color(0xFF1565C0),
                contentDescription = stringResource(
                    if (state.soundOn) {
                        R.string.overlay_voice_sound_on_cd
                    } else {
                        R.string.overlay_voice_sound_off_cd
                    },
                ),
                onClick = onSoundClick,
            )
        }
    }
}

@Composable
private fun VoiceQuickToggle(
    iconRes: Int,
    active: Boolean,
    accent: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val bg = if (active) accent else Color(0xCC37415C)
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = Color.White,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg, CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics { this.contentDescription = contentDescription },
    )
}

