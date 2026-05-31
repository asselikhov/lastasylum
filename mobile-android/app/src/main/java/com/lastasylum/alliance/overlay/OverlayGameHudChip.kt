package com.lastasylum.alliance.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HudIconSize = 16.dp
private val HudChipPaddingH = 6.dp
private val HudChipPaddingV = 5.dp
internal val HudRowSpacing = 8.dp
/** Stable width for top-right HUD — matches voice settings panel so chips do not shift on expand. */
internal val HudTopRightMinWidth = 280.dp
private val HudChipCorner = 6.dp
private val HudChipBorderWidth = 1.dp
private val HudBadgeOverflowPaddingTop = 10.dp
private val HudBadgeOverflowPaddingEnd = 10.dp
private val HudBadgeColor = Color(0xFFE53935)

/** Гармоничная палитра HUD-кнопок: свой оттенок фона, обводки и иконки. */
internal enum class OverlayHudChipAccent(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val icon: Color,
    val border: Color,
) {
    News(
        backgroundTop = Color(0xE01A3048),
        backgroundBottom = Color(0xE0101C28),
        icon = Color(0xFF82CFFF),
        border = Color(0x9958A8E0),
    ),
    Forum(
        backgroundTop = Color(0xE02A2240),
        backgroundBottom = Color(0xE0181228),
        icon = Color(0xFFD4A5F5),
        border = Color(0x999070B8),
    ),
    Mail(
        backgroundTop = Color(0xE0163834),
        backgroundBottom = Color(0xE00C2420),
        icon = Color(0xFF5EEAD4),
        border = Color(0x9938B8A8),
    ),
    Online(
        backgroundTop = Color(0xE01E3828),
        backgroundBottom = Color(0xE0122818),
        icon = Color(0xFF7AE582),
        border = Color(0x9950B860),
    ),
    Notifications(
        backgroundTop = Color(0xE03A3018),
        backgroundBottom = Color(0xE0282010),
        icon = Color(0xFFFFD180),
        border = Color(0x99C89048),
    ),
    Commands(
        backgroundTop = Color(0xE0382E18),
        backgroundBottom = Color(0xE0282010),
        icon = Color(0xFFFFC470),
        border = Color(0x99C89048),
    ),
    Voice(
        backgroundTop = Color(0xE0242840),
        backgroundBottom = Color(0xE016182C),
        icon = Color(0xFFA5B4FF),
        border = Color(0x997080C8),
    ),
    Sound(
        backgroundTop = Color(0xE01E2848),
        backgroundBottom = Color(0xE0121830),
        icon = Color(0xFF90CAF9),
        border = Color(0x996080D0),
    ),
    Mic(
        backgroundTop = Color(0xE0381828),
        backgroundBottom = Color(0xE0281018),
        icon = Color(0xFFF48FB1),
        border = Color(0x99B06080),
    ),
    Settings(
        backgroundTop = Color(0xE0302238),
        backgroundBottom = Color(0xE0201828),
        icon = Color(0xFFB39DDB),
        border = Color(0x998068A8),
    ),
    ;

    fun mutedIcon(fraction: Float = 0.72f): Color = icon.copy(alpha = fraction)
}

/** Включённый микрофон / звук в голосовом HUD. */
internal val HudVoiceActiveGreen = Color(0xFF66BB6A)

private val UpdateGoldDeep = Color(0xFFFFB300)
private val UpdateGoldBright = Color(0xFFFFE082)
private val UpdateGoldPale = Color(0xFFFFF8E1)
private val UpdateGoldAmber = Color(0xFFFF8F00)
private val UpdateIconTint = Color(0xFFFFCA28)

/** App-update chip: no fill, animated golden shimmer stroke. */
@Composable
internal fun OverlayGameHudUpdateChip(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "updateGoldBorder")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val corner = HudChipCorner
    val borderWidth = 1.5.dp
    val goldColors = remember {
        listOf(UpdateGoldDeep, UpdateGoldBright, UpdateGoldPale, UpdateGoldAmber, UpdateGoldDeep)
    }
    Box(modifier = modifier.wrapContentSize(unbounded = true)) {
        Box(
            modifier = Modifier
                .drawWithContent {
                    val strokePx = borderWidth.toPx()
                    val inset = strokePx / 2f
                    val w = size.width - strokePx
                    val h = size.height - strokePx
                    val phase = shimmer * (w + h)
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = goldColors,
                            start = Offset(-w + phase, 0f),
                            end = Offset(phase, h),
                        ),
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(w, h),
                        cornerRadius = CornerRadius(corner.toPx()),
                        style = Stroke(width = strokePx),
                        alpha = 0.65f + glow * 0.35f,
                    )
                    drawContent()
                }
                .clickable(onClick = onClick)
                .padding(horizontal = HudChipPaddingH, vertical = HudChipPaddingV)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = UpdateIconTint.copy(alpha = 0.88f + glow * 0.12f),
                modifier = Modifier.size(HudIconSize),
            )
        }
    }
}

@Composable
internal fun OverlayGameHudBar(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit,
) {
    val edgePadding = if (horizontalAlignment == Alignment.End) {
        PaddingValues(top = HudBadgeOverflowPaddingTop, start = HudBadgeOverflowPaddingEnd)
    } else {
        PaddingValues(top = HudBadgeOverflowPaddingTop, end = HudBadgeOverflowPaddingEnd)
    }
    Column(
        modifier = modifier.padding(edgePadding),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(HudRowSpacing),
    ) {
        content()
    }
}

@Composable
internal fun OverlayGameHudChip(
    accent: OverlayHudChipAccent,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    iconTint: Color? = null,
    icon: ImageVector? = null,
    painter: Painter? = null,
) {
    require(icon != null || painter != null) { "icon or painter required" }
    val badge = badgeCount.coerceAtLeast(0)
    val shape = RoundedCornerShape(HudChipCorner)
    val tint = iconTint ?: accent.icon
    Box(modifier = modifier.wrapContentSize(unbounded = true)) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.backgroundTop, accent.backgroundBottom),
                    ),
                    shape = shape,
                )
                .border(HudChipBorderWidth, accent.border.copy(alpha = 0.58f), shape)
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
            Badge(
                containerColor = HudBadgeColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .zIndex(2f)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp),
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

/** Вертикальный столбец кнопок HUD (голос: Звук над Микрофоном). */
@Composable
internal fun OverlayGameHudChipColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(HudRowSpacing),
        content = { content() },
    )
}

