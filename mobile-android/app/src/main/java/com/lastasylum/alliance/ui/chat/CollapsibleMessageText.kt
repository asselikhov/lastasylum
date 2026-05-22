package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

/** Сколько строк текста показывать в свёрнутом сообщении (чат комнат и темы форума). */
const val CHAT_MESSAGE_COLLAPSED_MAX_LINES = 15

/**
 * Длинный текст сообщения: до [collapsedMaxLines] строк, затем «Показать полностью» / «Свернуть».
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
) {
    if (text.isBlank()) return
    val saveKey = expandStateKey?.let { "collapsible_msg_$it" }
    var expanded by rememberSaveable(saveKey) { mutableStateOf(false) }
    var expandable by remember(text, collapsedMaxLines) { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = Modifier.fillMaxWidth(),
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                expandable = if (expanded) {
                    layout.lineCount > collapsedMaxLines
                } else {
                    layout.hasVisualOverflow
                }
            },
        )
        if (expandable) {
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
                    .padding(top = 5.dp)
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
