package com.lastasylum.alliance.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material Symbols Outlined for overlay quick-command category tabs.
 * Same stack as [OverlayHudIcons] (material-icons-extended).
 */
object OverlayQuickCommandIcons {
    val attack: ImageVector = Icons.Outlined.NearMe
    val storm: ImageVector = Icons.Outlined.Bolt
    val reinforcement: ImageVector = Icons.Outlined.Shield
    val push: ImageVector = Icons.AutoMirrored.Outlined.Send
    val reactions: ImageVector = Icons.Outlined.EmojiEmotions
    val target: ImageVector = Icons.Outlined.GpsFixed
    val search: ImageVector = Icons.Outlined.Search
}
