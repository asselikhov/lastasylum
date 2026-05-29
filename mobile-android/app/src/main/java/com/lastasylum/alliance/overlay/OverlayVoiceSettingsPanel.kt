package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import kotlin.math.roundToInt

private val VoicePanelBgTop = Color(0xF2141C2A)
private val VoicePanelBgBottom = Color(0xEE0C1018)
private val VoicePanelStroke = Color(0x3D4A62AA)
private val VoicePanelMuted = Color(0xFF9AB0C4D8)
private val VoicePanelAccent = Color(0xFF7986CB)
private val VoicePanelSliderActive = Color(0xFF66BB6A)

private const val VOLUME_SLIDER_STEPS = 29

@Composable
internal fun OverlayVoiceSettingsPanel(
    soundVolume: Float,
    micVolume: Float,
    onSoundVolumeChange: (Float) -> Unit,
    onMicVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 240.dp, max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, VoicePanelStroke, RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(listOf(VoicePanelBgTop, VoicePanelBgBottom)),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.overlay_voice_settings_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF4F7FF),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.overlay_online_close_cd),
                    tint = Color(0x99A8B4CC),
                )
            }
        }
        OverlayVoiceVolumeRow(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            label = stringResource(R.string.overlay_voice_settings_sound),
            volume = soundVolume,
            onVolumeChange = onSoundVolumeChange,
        )
        OverlayVoiceVolumeRow(
            icon = Icons.Outlined.Mic,
            label = stringResource(R.string.overlay_voice_settings_mic),
            volume = micVolume,
            onVolumeChange = onMicVolumeChange,
        )
    }
}

@Composable
private fun OverlayVoiceVolumeRow(
    icon: ImageVector,
    label: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VoicePanelAccent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = VoicePanelMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.overlay_voice_settings_volume_percent,
                    (volume * 100f).roundToInt().coerceIn(0, 150),
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE8F4FF),
            )
        }
        Slider(
            value = volume.coerceIn(
                UserSettingsPreferences.OVERLAY_VOICE_VOLUME_MIN,
                UserSettingsPreferences.OVERLAY_VOICE_VOLUME_MAX,
            ),
            onValueChange = onVolumeChange,
            valueRange = UserSettingsPreferences.OVERLAY_VOICE_VOLUME_MIN..
                UserSettingsPreferences.OVERLAY_VOICE_VOLUME_MAX,
            steps = VOLUME_SLIDER_STEPS,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = VoicePanelSliderActive,
                activeTrackColor = VoicePanelSliderActive,
                inactiveTrackColor = Color(0xFF2A3544),
            ),
        )
    }
}
