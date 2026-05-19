package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HudIconSize = 16.dp
private val HudChipPaddingH = 6.dp
private val HudChipPaddingV = 5.dp
private val HudRowSpacing = 4.dp
private val HudChipBackground = Color(0xB310141E)
private val HudChipCorner = 6.dp
private val HudBadgeOverflowPaddingTop = 6.dp
private val HudBadgeOverflowPaddingEnd = 6.dp

@Composable
internal fun OverlayGameHudBar(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.padding(
            top = HudBadgeOverflowPaddingTop,
            end = HudBadgeOverflowPaddingEnd,
        ),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(HudRowSpacing),
    ) {
        content()
    }
}

@Composable
internal fun OverlayGameHudChip(
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    icon: ImageVector? = null,
    painter: Painter? = null,
) {
    require(icon != null || painter != null) { "icon or painter required" }
    val badge = badgeCount.coerceAtLeast(0)
    Box(
        modifier = modifier.wrapContentSize(unbounded = true),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .background(HudChipBackground, RoundedCornerShape(HudChipCorner))
                .clickable(onClick = onClick)
                .padding(horizontal = HudChipPaddingH, vertical = HudChipPaddingV)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            when {
                icon != null -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(HudIconSize),
                    )
                }
                painter != null -> {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(HudIconSize),
                    )
                }
            }
        }
        if (badge > 0) {
            val badgeText = if (badge > 99) "99+" else badge.toString()
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-5).dp)
                    .background(Color(0xFFE53935), CircleShape)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 8.sp,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun OverlayGameHudChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HudRowSpacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
