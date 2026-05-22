package com.lastasylum.alliance.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

/** Сколько строк текста показывать в свёрнутом сообщении (чат комнат и темы форума). */
const val CHAT_MESSAGE_COLLAPSED_MAX_LINES = 15

/** Высота зоны плавного затухания текста у нижнего края свёрнутого блока. */
private val CollapsedTextFadeHeight = 52.dp

/**
 * Длинный текст: в свёрнутом виде обрезка по [collapsedMaxLines] с градиентным «замыливанием»
 * (без «…»), ссылка «Показать полностью» / «Свернуть».
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
    /** Цвет фона капли — для бесшовного градиента внизу свёрнутого текста. */
    fadeBaseColor: Color,
) {
    if (text.isBlank()) return
    val saveKey = expandStateKey?.let { "collapsible_msg_$it" }
    var expanded by rememberSaveable(saveKey) { mutableStateOf(false) }
    var exceedsLimit by remember(text, collapsedMaxLines) { mutableStateOf(false) }

    Column(
        modifier = modifier.animateContentSize(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                style = style,
                color = color,
                modifier = Modifier.fillMaxWidth(),
                maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Clip,
                onTextLayout = { layout ->
                    exceedsLimit = if (expanded) {
                        layout.lineCount > collapsedMaxLines
                    } else {
                        layout.hasVisualOverflow
                    }
                },
            )
            if (!expanded && exceedsLimit) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(CollapsedTextFadeHeight)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.42f to fadeBaseColor.copy(alpha = 0.55f),
                                0.78f to fadeBaseColor.copy(alpha = 0.92f),
                                1f to fadeBaseColor,
                            ),
                        ),
                )
            }
        }
        if (exceedsLimit) {
            val toggleLabel = if (expanded) {
                stringResource(R.string.chat_message_collapse)
            } else {
                stringResource(R.string.chat_message_expand)
            }
            Text(
                text = toggleLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = expandLinkColor,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = true,
                            color = expandLinkColor.copy(alpha = 0.25f),
                        ),
                        onClick = { expanded = !expanded },
                    ),
            )
        }
    }
}
