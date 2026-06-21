package com.lastasylum.alliance.ui.chat

import android.graphics.Rect
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import com.lastasylum.alliance.R
import com.lastasylum.alliance.game.GameMapNavigator
import com.lastasylum.alliance.game.MapCoordinateParser
import kotlin.math.roundToInt

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
    /** Compose-root bounds of the coordinate link (for overlay strip touch capture). */
    onCoordinateLinkBoundsInRoot: ((Rect?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coordRange = remember(text) { MapCoordinateParser.coordinateRangeIn(text) }
    val goToMapLabel = stringResource(R.string.map_coord_go_to_map)

    DisposableEffect(onCoordinateLinkBoundsInRoot, coordRange) {
        if (onCoordinateLinkBoundsInRoot != null && coordRange == null) {
            onCoordinateLinkBoundsInRoot(null)
        }
        onDispose {
            if (onCoordinateLinkBoundsInRoot != null && coordRange != null) {
                onCoordinateLinkBoundsInRoot(null)
            }
        }
    }

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

    val onOpenMap: () -> Unit = {
        GameMapNavigator.openFromMessage(context, text)
    }

    val annotated = remember(text, coordRange, linkColor) {
        buildAnnotatedString {
            if (coordRange.first > 0) {
                append(text.substring(0, coordRange.first))
            }
            withLink(
                LinkAnnotation.Clickable(
                    tag = MAP_COORD_LINK_TAG,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    linkInteractionListener = { onOpenMap() },
                ),
            ) {
                append(text.substring(coordRange.first, coordRange.last + 1))
            }
            val after = coordRange.last + 1
            if (after < text.length) {
                append(text.substring(after))
            }
        }
    }

    val linkBoundsModifier = if (onCoordinateLinkBoundsInRoot != null) {
        Modifier.onGloballyPositioned { coords ->
            val b = coords.boundsInRoot()
            onCoordinateLinkBoundsInRoot(
                Rect(
                    b.left.roundToInt(),
                    b.top.roundToInt(),
                    b.right.roundToInt(),
                    b.bottom.roundToInt(),
                ),
            )
        }
    } else {
        Modifier
    }

    Text(
        text = annotated,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
            .then(linkBoundsModifier)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(goToMapLabel) {
                        onOpenMap()
                        true
                    },
                )
            },
    )
}

private const val MAP_COORD_LINK_TAG = "map_coord"
