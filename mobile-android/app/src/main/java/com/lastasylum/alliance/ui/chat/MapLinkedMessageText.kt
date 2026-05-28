package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.lastasylum.alliance.R
import com.lastasylum.alliance.game.GameMapNavigator
import com.lastasylum.alliance.game.MapCoordinateParser

/**
 * Message body with a clickable `X:… Y:…` suffix when parseable; otherwise [CollapsibleMessageText].
 */
@Composable
fun MapLinkedMessageText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = CHAT_MESSAGE_COLLAPSED_MAX_LINES,
    expandStateKey: String? = null,
    expandLinkColor: Color = color.copy(alpha = 0.92f),
    fadeBaseColor: Color,
    linkColor: Color = expandLinkColor,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val context = LocalContext.current
    val coordRange = remember(text) { MapCoordinateParser.coordinateRangeIn(text) }
    val goToMapLabel = stringResource(R.string.map_coord_go_to_map)

    if (coordRange == null) {
        if (maxLines < Int.MAX_VALUE) {
            Text(
                text = text,
                style = style,
                color = color,
                modifier = modifier,
                maxLines = maxLines,
                overflow = overflow,
            )
        } else {
            CollapsibleMessageText(
                text = text,
                style = style,
                color = color,
                modifier = modifier,
                collapsedMaxLines = collapsedMaxLines,
                expandStateKey = expandStateKey,
                expandLinkColor = expandLinkColor,
                fadeBaseColor = fadeBaseColor,
            )
        }
        return
    }

    val annotated = remember(text, coordRange, linkColor) {
        buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                ),
                coordRange.first,
                coordRange.last + 1,
            )
            addStringAnnotation(
                tag = MAP_COORD_LINK_TAG,
                annotation = MAP_COORD_LINK_TAG,
                start = coordRange.first,
                end = coordRange.last + 1,
            )
        }
    }

    val onOpenMap: () -> Unit = {
        GameMapNavigator.openFromMessage(context, text)
    }

    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(goToMapLabel) {
                    onOpenMap()
                    true
                },
            )
        },
        onClick = { offset ->
            annotated.getStringAnnotations(MAP_COORD_LINK_TAG, offset, offset)
                .firstOrNull()
                ?.let { onOpenMap() }
        },
    )
}

private const val MAP_COORD_LINK_TAG = "map_coord"
