package com.lastasylum.alliance.ui.chat

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.lastasylum.alliance.R
import com.lastasylum.alliance.game.GameMapNavigator
import com.lastasylum.alliance.game.MapCoordinateParser
import com.lastasylum.alliance.game.RaidShareGlyphs
import com.lastasylum.alliance.overlay.LocalOverlayDismissBeforeMapNavigate
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

    val dismissBeforeMap = LocalOverlayDismissBeforeMapNavigate.current
    val onOpenMapState = rememberUpdatedState {
        dismissBeforeMap?.invoke()
        GameMapNavigator.openFromMessage(context, text)
    }

    val annotated = remember(text, coordRange, linkColor) {
        buildAnnotatedString {
            if (coordRange.first > 0) {
                appendRich(text.substring(0, coordRange.first))
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
                    linkInteractionListener = { onOpenMapState.value.invoke() },
                ),
            ) {
                append(text.substring(coordRange.first, coordRange.last + 1))
            }
            val after = coordRange.last + 1
            if (after < text.length) {
                appendRich(text.substring(after))
            }
        }
    }

    val inlineContent = mapOf(
        INLINE_POWER to raidStatInlineIcon(R.drawable.ic_overlay_game_power),
        INLINE_KILLS to raidStatInlineIcon(R.drawable.ic_overlay_game_kills),
    )

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
        inlineContent = inlineContent,
        modifier = modifier
            .then(linkBoundsModifier)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(goToMapLabel) {
                        onOpenMapState.value.invoke()
                        true
                    },
                )
            },
    )
}

private const val MAP_COORD_LINK_TAG = "map_coord"
private const val INLINE_POWER = "raid_icon_power"
private const val INLINE_KILLS = "raid_icon_kills"

/** Инлайновая иконка статистики рейда (Мощь/Поверженные) размером со строку текста. */
private fun raidStatInlineIcon(drawableRes: Int): InlineTextContent =
    InlineTextContent(
        Placeholder(
            width = 1.25.em,
            height = 1.05.em,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        ),
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
        )
    }

/** Грейды сундуков с цветом: SR — синий, SSR — фиолетовый, UR — золотой. */
private val GRADE_BLUE = Color(0xFF60A5FA)
private val GRADE_PURPLE = Color(0xFFC084FC)
private val GRADE_GOLD = Color(0xFFFBBF24)

// Грейд (SSR раньше SR в альтернации) + опциональные звёзды «★».
private val GRADE_REGEX = Regex("(?<![A-Za-z])(SSR|SR|UR)(\\s*\u2605+)?")

private fun gradeColor(token: String): Color = when (token) {
    "SR" -> GRADE_BLUE
    "SSR" -> GRADE_PURPLE
    "UR" -> GRADE_GOLD
    else -> GRADE_BLUE
}

/**
 * Добавляет сегмент текста, заменяя маркеры [RaidShareGlyphs] на инлайновые иконки
 * (Мощь/Поверженные) и подсвечивая грейды сундуков.
 */
private fun AnnotatedString.Builder.appendRich(segment: String) {
    val buf = StringBuilder()
    fun flush() {
        if (buf.isNotEmpty()) {
            appendWithGradeColors(buf.toString())
            buf.setLength(0)
        }
    }
    for (ch in segment) {
        when (ch) {
            RaidShareGlyphs.POWER -> { flush(); appendInlineContent(INLINE_POWER, "\u26A1") }
            RaidShareGlyphs.KILLS -> { flush(); appendInlineContent(INLINE_KILLS, "\u2694") }
            else -> buf.append(ch)
        }
    }
    flush()
}

/** Добавляет текст, подсвечивая токены грейда сундука (SR/SSR/UR) и идущие за ними звёзды. */
private fun AnnotatedString.Builder.appendWithGradeColors(segment: String) {
    var last = 0
    for (m in GRADE_REGEX.findAll(segment)) {
        if (m.range.first > last) append(segment.substring(last, m.range.first))
        withStyle(SpanStyle(color = gradeColor(m.groupValues[1]), fontWeight = FontWeight.Bold)) {
            append(segment.substring(m.range.first, m.range.last + 1))
        }
        last = m.range.last + 1
    }
    if (last < segment.length) append(segment.substring(last))
}
