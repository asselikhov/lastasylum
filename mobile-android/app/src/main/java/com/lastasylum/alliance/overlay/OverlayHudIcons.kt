package com.lastasylum.alliance.overlay

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.lastasylum.alliance.R

/**
 * Unified overlay HUD icon set — custom vectors with matching stroke weight and padding.
 * Tint via [OverlayGameHudChip] accent colors.
 */
object OverlayHudIcons {
    @DrawableRes val news: Int = R.drawable.ic_overlay_hud_news
    @DrawableRes val forum: Int = R.drawable.ic_overlay_hud_forum
    @DrawableRes val mail: Int = R.drawable.ic_overlay_hud_mail
    @DrawableRes val team: Int = R.drawable.ic_overlay_hud_team
    @DrawableRes val notifications: Int = R.drawable.ic_overlay_hud_notifications
    @DrawableRes val voice: Int = R.drawable.ic_overlay_hud_voice
    @DrawableRes val quickCommands: Int = R.drawable.ic_overlay_quick_commands
    @DrawableRes val settings: Int = R.drawable.ic_overlay_hud_settings
    @DrawableRes val volumeOn: Int = R.drawable.ic_overlay_volume_on
    @DrawableRes val volumeOff: Int = R.drawable.ic_overlay_volume_off
    @DrawableRes val micOn: Int = R.drawable.ic_overlay_mic_on
    @DrawableRes val micOff: Int = R.drawable.ic_overlay_mic_off
    @DrawableRes val appUpdate: Int = R.drawable.ic_overlay_hud_update

    @Composable
    fun painter(@DrawableRes id: Int): Painter = painterResource(id)
}
