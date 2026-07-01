package com.lastasylum.alliance.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Semantic map of overlay HUD icons — Material Symbols Outlined
 * ([material-icons-extended](https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary)).
 *
 * Matches icons used elsewhere in the app (e.g. [com.lastasylum.alliance.ui.screens.TeamScreen],
 * [com.lastasylum.alliance.ui.Navigation]) for visual consistency.
 */
object OverlayHudIcons {
    val news: ImageVector = Icons.AutoMirrored.Outlined.Article
    val forum: ImageVector = Icons.Outlined.Forum
    val mail: ImageVector = Icons.Outlined.ChatBubbleOutline
    val teleport: ImageVector = Icons.Outlined.NearMe
    val search: ImageVector = Icons.Outlined.Search
    val team: ImageVector = Icons.Outlined.Groups
    val notifications: ImageVector = Icons.Outlined.Notifications
    val voice: ImageVector = Icons.Outlined.HeadsetMic
    val quickCommands: ImageVector = Icons.Outlined.RocketLaunch
    val settings: ImageVector = Icons.Outlined.Tune
    val volumeOn: ImageVector = Icons.AutoMirrored.Outlined.VolumeUp
    val volumeOff: ImageVector = Icons.AutoMirrored.Outlined.VolumeOff
    val micOn: ImageVector = Icons.Outlined.Mic
    val micOff: ImageVector = Icons.Outlined.MicOff
    val appUpdate: ImageVector = Icons.Outlined.SystemUpdate
}
