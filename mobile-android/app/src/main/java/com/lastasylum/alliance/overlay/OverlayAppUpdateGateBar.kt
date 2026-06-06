package com.lastasylum.alliance.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R

private val GateBarCorner = 8.dp
private val GateBarBorderWidth = 1.dp
private val GateBarPaddingH = 10.dp
private val GateBarPaddingV = 6.dp
private val GateBarIconSize = 16.dp
private val GateBarFadeWidth = 8.dp

private val GateBackgroundTop = Color(0xE01A2030)
private val GateBackgroundBottom = Color(0xE010141E)
private val GateTextColor = Color(0xFFF1F5FF)
private val GateUpdateGoldDeep = Color(0xFFFFB300)
private val GateUpdateGoldBright = Color(0xFFFFE082)
private val GateUpdateGoldPale = Color(0xFFFFF8E1)
private val GateUpdateGoldAmber = Color(0xFFFF8F00)
private val GateUpdateIconTint = Color(0xFFFFCA28)
private val GateUpdateButtonText = Color(0xFF1A1408)

@Composable
fun OverlayAppUpdateGateBar(
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gateDescription = stringResource(R.string.overlay_app_update_gate_cd)
    val message = stringResource(R.string.overlay_app_update_gate_message)
    val actionLabel = stringResource(R.string.overlay_app_update_gate_action)

    val transition = rememberInfiniteTransition(label = "gateGoldBorder")
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
    val goldColors = remember {
        listOf(
            GateUpdateGoldDeep,
            GateUpdateGoldBright,
            GateUpdateGoldPale,
            GateUpdateGoldAmber,
            GateUpdateGoldDeep,
        )
    }
    val shape = RoundedCornerShape(GateBarCorner)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = gateDescription }
            .drawWithContent {
                val strokePx = GateBarBorderWidth.toPx()
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
                    cornerRadius = CornerRadius(GateBarCorner.toPx()),
                    style = Stroke(width = strokePx),
                    alpha = 0.65f + glow * 0.35f,
                )
                drawContent()
            }
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GateBackgroundTop, GateBackgroundBottom),
                ),
            )
            .padding(horizontal = GateBarPaddingH, vertical = GateBarPaddingV),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = GateUpdateIconTint.copy(alpha = 0.88f + glow * 0.12f),
                modifier = Modifier.size(GateBarIconSize),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = message,
                    color = GateTextColor.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            velocity = 28.dp,
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(GateBarFadeWidth)
                        .height(18.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    GateBackgroundTop,
                                    GateBackgroundTop.copy(alpha = 0f),
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(GateBarFadeWidth)
                        .height(18.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    GateBackgroundBottom.copy(alpha = 0f),
                                    GateBackgroundBottom,
                                ),
                            ),
                        ),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OverlayAppUpdateGateActionButton(
                label = actionLabel,
                glow = glow,
                onClick = onUpdateClick,
            )
        }
    }
}

@Composable
private fun OverlayAppUpdateGateActionButton(
    label: String,
    glow: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(GateUpdateGoldAmber, GateUpdateGoldBright, GateUpdateGoldDeep),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = GateUpdateButtonText.copy(alpha = 0.92f + glow * 0.06f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}
