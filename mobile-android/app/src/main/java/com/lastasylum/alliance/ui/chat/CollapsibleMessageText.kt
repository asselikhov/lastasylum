package com.lastasylum.alliance.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

/** Сколько строк текста в свёрнутом длинном сообщении (как в Telegram). */
const val CHAT_MESSAGE_COLLAPSED_MAX_LINES = 15

private val CollapsedFadeHeight = 28.dp
private val ExpandLinkStyle = TextStyle(
    fontWeight = FontWeight.Medium,
)

private val ContentSizeAnim = tween<IntSize>(
    durationMillis = 220,
    easing = FastOutSlowInEasing,
)

/**
 * Длинный текст в пузыре: обрезка по [collapsedMaxLines], градиент внизу, «читать далее» /
 * «свернуть» в стиле Telegram (без «…»).
 */
@Composable
fun CollapsibleMessageText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = CHAT_MESSAGE_COLLAPSED_MAX_LINES,
    expandStateKey: String? = null,
    expandLinkColor: Color = color.copy(alpha = 0.88f),
    /** Цвет фона капли — градиент затухания сливается с ним. */
    fadeBaseColor: Color,
) {
    if (text.isBlank()) return
    val saveKey = expandStateKey?.let { "collapsible_msg_$it" }
    var expanded by rememberSaveable(saveKey) { mutableStateOf(false) }
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    val isLong = remember(text, collapsedMaxLines, layoutResult) {
        val layout = layoutResult ?: return@remember false
        layout.lineCount > collapsedMaxLines ||
            (!expanded && layout.hasVisualOverflow)
    }

    val linkStyle = ExpandLinkStyle.merge(style)
    val expandLabel = stringResource(R.string.chat_message_expand)
    val collapseLabel = stringResource(R.string.chat_message_collapse)

    Column(
        modifier = modifier.animateContentSize(animationSpec = ContentSizeAnim),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                style = style,
                color = color,
                modifier = Modifier.fillMaxWidth(),
                maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Clip,
                onTextLayout = { layoutResult = it },
            )

            if (isLong && !expanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(CollapsedFadeHeight)
                        .drawWithContent {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to fadeBaseColor.copy(alpha = 0f),
                                    0.35f to fadeBaseColor.copy(alpha = 0.72f),
                                    0.72f to fadeBaseColor.copy(alpha = 0.96f),
                                    1f to fadeBaseColor,
                                ),
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { expanded = true },
                        ),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        text = expandLabel,
                        style = linkStyle,
                        color = expandLinkColor,
                        modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                    )
                }
            }
        }

        if (isLong && expanded) {
            Text(
                text = collapseLabel,
                style = linkStyle,
                color = expandLinkColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expanded = false },
                    ),
            )
        }
    }
}
