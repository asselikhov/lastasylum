package com.lastasylum.alliance.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R

data class OverlayGameTopRightHudState(
    /** Teammates in game with a fresh overlay heartbeat (see [OverlayGameStatusHudRefresh.filterTeamIngameOverlayMembers]). */
    val onlineIngameCount: Int = 0,
    val teamJoinRequestCount: Int = 0,
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
    OverlayGameHudBar(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        OverlayGameHudChipRow {
            OverlayGameHudChip(
                icon = Icons.Outlined.Groups,
                tint = Color(0xFF81C784),
                badgeCount = state.teamJoinRequestCount,
                contentDescription = if (state.teamJoinRequestCount > 0) {
                    stringResource(
                        R.string.overlay_hud_join_requests_cd,
                        state.teamJoinRequestCount,
                    )
                } else {
                    stringResource(
                        R.string.overlay_hud_online_cd,
                        state.onlineIngameCount,
                    )
                },
                onClick = onOnlineClick,
            )
            OverlayGameHudChip(
                painter = painterResource(R.drawable.ic_overlay_quick_commands),
                tint = Color(0xFFFFB74D),
                contentDescription = stringResource(R.string.overlay_cd_commands),
                onClick = onQuickCommandsClick,
            )
            OverlayGameHudChip(
                icon = Icons.Outlined.RecordVoiceOver,
                tint = if (state.voiceExpanded || state.micOn || state.soundOn) {
                    Color(0xFF7986CB)
                } else {
                    Color(0xFF9FA8DA)
                },
                contentDescription = stringResource(
                    if (state.voiceExpanded) {
                        R.string.overlay_voice_hub_collapse_cd
                    } else {
                        R.string.overlay_voice_hub_cd
                    },
                ),
                onClick = onVoiceHubClick,
            )
        }
        if (state.voiceExpanded) {
            OverlayGameHudChipColumn(horizontalAlignment = Alignment.End) {
                OverlayGameHudChip(
                    icon = if (state.soundOn) {
                        Icons.AutoMirrored.Outlined.VolumeUp
                    } else {
                        Icons.AutoMirrored.Outlined.VolumeOff
                    },
                    tint = if (state.soundOn) HudVoiceActiveGreen else HudVoiceInactiveTint,
                    contentDescription = stringResource(
                        if (state.soundOn) {
                            R.string.overlay_voice_sound_on_cd
                        } else {
                            R.string.overlay_voice_sound_off_cd
                        },
                    ),
                    onClick = onSoundClick,
                )
                OverlayGameHudChip(
                    icon = if (state.micOn) Icons.Outlined.Mic else Icons.Outlined.MicOff,
                    tint = if (state.micOn) HudVoiceActiveGreen else HudVoiceInactiveTint,
                    contentDescription = stringResource(
                        if (state.micOn) {
                            R.string.overlay_voice_mic_on_cd
                        } else {
                            R.string.overlay_voice_mic_off_cd
                        },
                    ),
                    onClick = onMicClick,
                )
            }
        }
    }
}
