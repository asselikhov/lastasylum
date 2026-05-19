package com.lastasylum.alliance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

@Composable
fun OverlayMemberVoiceBadges(
    micOn: Boolean,
    soundOn: Boolean,
    modifier: Modifier = Modifier,
) {
    val micCd = stringResource(
        if (micOn) R.string.team_member_voice_mic_on_cd else R.string.team_member_voice_mic_off_cd,
    )
    val soundCd = stringResource(
        if (soundOn) R.string.team_member_voice_sound_on_cd else R.string.team_member_voice_sound_off_cd,
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayMemberVoiceBadge(
            iconRes = if (micOn) R.drawable.ic_overlay_mic_on else R.drawable.ic_overlay_mic_off,
            contentDescription = micCd,
            accent = Color(0xFF2E7D32),
            accentGlow = Color(0xFF81C784),
            active = micOn,
        )
        OverlayMemberVoiceBadge(
            iconRes = if (soundOn) R.drawable.ic_overlay_volume_on else R.drawable.ic_overlay_volume_off,
            contentDescription = soundCd,
            accent = Color(0xFF1565C0),
            accentGlow = Color(0xFF64B5F6),
            active = soundOn,
        )
    }
}

@Composable
private fun OverlayMemberVoiceBadge(
    iconRes: Int,
    contentDescription: String,
    accent: Color,
    accentGlow: Color,
    active: Boolean,
) {
    val idleBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val idleBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (active) {
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.92f),
                            accent.copy(alpha = 0.72f),
                        ),
                    )
                } else {
                    Brush.linearGradient(listOf(idleBg, idleBg))
                },
            )
            .border(
                width = 1.dp,
                color = if (active) accentGlow.copy(alpha = 0.55f) else idleBorder,
                shape = RoundedCornerShape(7.dp),
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = if (active) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
        )
    }
}
