package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

data class OverlayGameTopRightHudState(
    /** Teammates in game with a fresh overlay heartbeat (see [OverlayGameStatusHudRefresh.filterTeamIngameOverlayMembers]). */
    val onlineIngameCount: Int = 0,
    val teamJoinRequestCount: Int = 0,
    val reactionLogUnreadCount: Int = 0,
    val micOn: Boolean = false,
    val soundOn: Boolean = false,
    val voiceExpanded: Boolean = false,
    val voiceSettingsVisible: Boolean = false,
    val soundVolume: Float = 1f,
    val micVolume: Float = 1f,
)

@Composable
fun OverlayGameTopRightHud(
    state: OverlayGameTopRightHudState,
    onNotificationsClick: () -> Unit,
    onOnlineClick: () -> Unit,
    onQuickCommandsClick: () -> Unit,
    onVoiceHubClick: () -> Unit,
    onMicClick: () -> Unit,
    onSoundClick: () -> Unit,
    onVoiceSettingsClick: () -> Unit,
    onSoundVolumeChange: (Float) -> Unit,
    onMicVolumeChange: (Float) -> Unit,
    onVoiceSettingsDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .widthIn(min = HudTopRightMinWidth)
            .wrapContentSize(align = Alignment.TopEnd),
    ) {
        var mainBarHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val voiceTopPadding = with(density) { mainBarHeightPx.toDp() } + HudRowSpacing

        if (state.voiceExpanded) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = voiceTopPadding),
                horizontalAlignment = Alignment.End,
            ) {
                OverlayGameHudChipColumn(horizontalAlignment = Alignment.End) {
                    OverlayGameHudChip(
                        icon = if (state.soundOn) {
                            Icons.AutoMirrored.Outlined.VolumeUp
                        } else {
                            Icons.AutoMirrored.Outlined.VolumeOff
                        },
                        accent = OverlayHudChipAccent.Sound,
                        iconTint = if (state.soundOn) HudVoiceActiveGreen else null,
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
                        accent = OverlayHudChipAccent.Mic,
                        iconTint = if (state.micOn) HudVoiceActiveGreen else null,
                        contentDescription = stringResource(
                            if (state.micOn) {
                                R.string.overlay_voice_mic_on_cd
                            } else {
                                R.string.overlay_voice_mic_off_cd
                            },
                        ),
                        onClick = onMicClick,
                    )
                    OverlayGameHudChip(
                        icon = Icons.Outlined.Settings,
                        accent = OverlayHudChipAccent.Settings,
                        iconTint = if (state.voiceSettingsVisible) {
                            OverlayHudChipAccent.Settings.icon
                        } else {
                            OverlayHudChipAccent.Settings.mutedIcon()
                        },
                        contentDescription = stringResource(R.string.overlay_voice_settings_cd),
                        onClick = onVoiceSettingsClick,
                    )
                }
                if (state.voiceSettingsVisible) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OverlayVoiceSettingsPanel(
                        soundVolume = state.soundVolume,
                        micVolume = state.micVolume,
                        onSoundVolumeChange = onSoundVolumeChange,
                        onMicVolumeChange = onMicVolumeChange,
                        onDismiss = onVoiceSettingsDismiss,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .onSizeChanged { mainBarHeightPx = it.height },
        ) {
            OverlayGameHudBar(horizontalAlignment = Alignment.End) {
                OverlayGameHudChipRow {
                    OverlayGameHudChip(
                        icon = Icons.Outlined.Groups,
                        accent = OverlayHudChipAccent.Online,
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
                        accent = OverlayHudChipAccent.Commands,
                        contentDescription = stringResource(R.string.overlay_cd_commands),
                        onClick = onQuickCommandsClick,
                    )
                    OverlayGameHudChip(
                        icon = Icons.Outlined.Notifications,
                        accent = OverlayHudChipAccent.Notifications,
                        badgeCount = state.reactionLogUnreadCount,
                        contentDescription = if (state.reactionLogUnreadCount > 0) {
                            stringResource(
                                R.string.overlay_hud_notifications_unread_cd,
                                state.reactionLogUnreadCount,
                            )
                        } else {
                            stringResource(R.string.overlay_hud_notifications_cd)
                        },
                        onClick = onNotificationsClick,
                    )
                    OverlayGameHudChip(
                        icon = Icons.Outlined.RecordVoiceOver,
                        accent = OverlayHudChipAccent.Voice,
                        iconTint = if (state.voiceExpanded || state.micOn || state.soundOn) {
                            OverlayHudChipAccent.Voice.icon
                        } else {
                            OverlayHudChipAccent.Voice.mutedIcon()
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
            }
        }
    }
}
