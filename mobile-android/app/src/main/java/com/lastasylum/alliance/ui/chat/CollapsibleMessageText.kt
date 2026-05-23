package com.lastasylum.alliance.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

/** Строк в свёрнутом длинном сообщении — достаточно, чтобы видеть начало и намёк на продолжение. */
const val CHAT_MESSAGE_COLLAPSED_MAX_LINES = 8

private val CollapsedFadeHeight = 56.dp
private val ToggleRowMinHeight = 36.dp
private val ToggleCorner = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)

private val ContentSizeAnim = tween<IntSize>(
    durationMillis = 280,
    easing = FastOutSlowInEasing,
)

/**
 * Длинный текст: ellipsis, мягкий fade к фону пузыря и отдельная строка «Показать полностью» / «Свернуть».
 */
@Composable
fun CollapsibleMessageText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = CHAT_MESSAGE_COLLAPSED_MAX_LINES,
    expandStateKey: String? = null,
    expandLinkColor: Color = color.copy(alpha = 0.92f),
    fadeBaseColor: Color,
) {
    if (text.isBlank()) return
    val saveKey = expandStateKey?.let { "collapsible_msg_$it" }
    var expanded by rememberSaveable(saveKey) { mutableStateOf(false) }
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    val isLong = remember(text, collapsedMaxLines, layoutResult, expanded) {
        val layout = layoutResult ?: return@remember false
        layout.lineCount > collapsedMaxLines ||
            (!expanded && layout.hasVisualOverflow)
    }

    val expandLabel = stringResource(R.string.chat_message_expand)
    val collapseLabel = stringResource(R.string.chat_message_collapse)
    val expandCd = stringResource(R.string.chat_message_expand_cd)
    val collapseCd = stringResource(R.string.chat_message_collapse_cd)

    Column(
        modifier = modifier.animateContentSize(animationSpec = ContentSizeAnim),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
        ) {
            Text(
                text = text,
                style = style,
                color = color,
                modifier = Modifier.fillMaxWidth(),
                maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                onTextLayout = { layoutResult = it },
            )

            if (isLong && !expanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(CollapsedFadeHeight)
                        .drawWithContent {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to fadeBaseColor.copy(alpha = 0f),
                                        0.35f to fadeBaseColor.copy(alpha = 0.35f),
                                        0.65f to fadeBaseColor.copy(alpha = 0.72f),
                                        0.88f to fadeBaseColor.copy(alpha = 0.94f),
                                        1f to fadeBaseColor,
                                    ),
                                ),
                            )
                        },
                )
            }
        }

        if (isLong) {
            MessageTextExpandToggle(
                expanded = expanded,
                expandLabel = expandLabel,
                collapseLabel = collapseLabel,
                contentDescription = if (expanded) collapseCd else expandCd,
                accentColor = expandLinkColor,
                fadeBaseColor = fadeBaseColor,
                onToggle = { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun MessageTextExpandToggle(
    expanded: Boolean,
    expandLabel: String,
    collapseLabel: String,
    contentDescription: String,
    accentColor: Color,
    fadeBaseColor: Color,
    onToggle: () -> Unit,
) {
    val label = if (expanded) collapseLabel else expandLabel
    val icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore
    val rowBg = lerp(fadeBaseColor, accentColor, 0.07f)
    val dividerColor = accentColor.copy(alpha = 0.2f)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing * 0.85f,
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp),
            thickness = 0.5.dp,
            color = dividerColor,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ToggleCorner)
                .background(rowBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = accentColor.copy(alpha = 0.18f)),
                    onClick = onToggle,
                )
                .semantics {
                    role = Role.Button
                }
                .heightIn(min = ToggleRowMinHeight)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = labelStyle,
                color = accentColor,
            )
        }
    }
}
