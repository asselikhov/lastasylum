package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.components.team.FeedCardDesignTokens.minPollBarProgress
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import kotlin.math.roundToInt

@Composable
fun JournalPollBadge(
    modifier: Modifier = Modifier,
    votesLabel: String? = null,
) {
    Row(
        modifier = modifier
            .clip(PremiumJournalFeedTokens.chipShape)
            .background(Color(0xFF818CF8).copy(alpha = 0.18f))
            .border(
                0.75.dp,
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF818CF8).copy(alpha = 0.45f),
                        PremiumColors.accentPurpleDeep.copy(alpha = 0.35f),
                    ),
                ),
                PremiumJournalFeedTokens.chipShape,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Poll,
            contentDescription = null,
            tint = Color(0xFFCBD5FF),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.team_news_poll_badge),
            style = PremiumJournalFeedTokens.metaStyle.copy(
                color = Color(0xFFCBD5FF),
                fontWeight = FontWeight.SemiBold,
            ),
        )
        votesLabel?.let { label ->
            Text(
                text = label,
                style = PremiumJournalFeedTokens.metaStyle,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PremiumJournalProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    barHeight: androidx.compose.ui.unit.Dp = 8.dp,
    selected: Boolean = false,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val visible = if (clamped > 0f) clamped.coerceAtLeast(minPollBarProgress) else 0f
    val animated by animateFloatAsState(
        targetValue = visible,
        animationSpec = if (animate) {
            tween(420, easing = FastOutSlowInEasing)
        } else {
            tween(0)
        },
        label = "journalPollProgress",
    )
    val trackShape = RoundedCornerShape(10.dp)
    val fillBrush = if (selected) {
        Brush.horizontalGradient(
            listOf(
                PremiumColors.accentCyan,
                Color(0xFF818CF8),
                PremiumColors.accentPurpleDeep,
            ),
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                PremiumColors.accentCyan.copy(alpha = 0.75f),
                Color(0xFF6366F1).copy(alpha = 0.65f),
            ),
        )
    }
    Box(
        modifier = modifier
            .height(barHeight)
            .clip(trackShape)
            .background(Color(0xFF1E2836).copy(alpha = 0.72f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(trackShape)
                .background(fillBrush),
        )
    }
}

@Composable
fun JournalPollOptionTile(
    text: String,
    share: Float,
    voteCount: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    onSelect: (() -> Unit)? = null,
    showVoteCount: Boolean = true,
    contentBelow: @Composable (() -> Unit)? = null,
) {
    val pct = if (share > 0f) (share * 100f).roundToInt() else 0
    val shape = PremiumJournalFeedTokens.optionTileShape
    val borderBrush = if (selected) {
        Brush.linearGradient(
            listOf(
                PremiumColors.accentCyan.copy(alpha = 0.65f),
                Color(0xFF818CF8).copy(alpha = 0.55f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.05f),
            ),
        )
    }
    val tileModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF3A4658).copy(alpha = if (selected) 0.55f else 0.38f),
                    Color(0xFF2A3444).copy(alpha = if (selected) 0.48f else 0.32f),
                ),
            ),
        )
        .border(1.dp, borderBrush, shape)
        .then(
            if (interactive && onSelect != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelect,
                )
            } else {
                Modifier
            },
        )
        .padding(
            horizontal = PremiumJournalFeedTokens.optionTilePaddingH,
            vertical = PremiumJournalFeedTokens.optionTilePaddingV,
        )

    Column(
        modifier = tileModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (interactive && onSelect != null) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                    modifier = Modifier.size(28.dp),
                    colors = RadioButtonDefaults.colors(
                        selectedColor = PremiumColors.accentCyan,
                        unselectedColor = PremiumJournalFeedTokens.metaColor,
                    ),
                )
            }
            Text(
                text = text,
                style = PremiumJournalFeedTokens.optionLabelStyle.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (pct > 0) "$pct%" else "—",
                style = PremiumJournalFeedTokens.optionPctStyle.copy(
                    color = if (selected) PremiumColors.accentCyanBright else PremiumJournalFeedTokens.metaColor,
                ),
                modifier = Modifier.widthIn(min = 36.dp),
            )
        }
        PremiumJournalProgressBar(
            progress = share,
            selected = selected,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showVoteCount && voteCount > 0) {
            Text(
                text = stringResource(R.string.team_news_poll_option_votes, voteCount),
                style = PremiumJournalFeedTokens.metaStyle,
            )
        }
        contentBelow?.invoke()
    }
}

@Composable
fun JournalPollPreviewBlock(
    options: List<com.lastasylum.alliance.data.teams.TeamNewsPollOptionDto>,
    tallies: List<com.lastasylum.alliance.data.teams.TeamNewsPollTallyDto>,
    myVoteOptionId: String?,
    modifier: Modifier = Modifier,
    maxOptions: Int = 2,
) {
    val totalVotes = tallies.sumOf { it.count }
    val previewOptions = if (options.size <= maxOptions) {
        options
    } else {
        options.sortedByDescending { opt ->
            tallies.find { it.optionId == opt.id }?.count ?: 0
        }.take(maxOptions)
    }
    val remaining = (options.size - previewOptions.size).coerceAtLeast(0)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        previewOptions.forEach { opt ->
            val cnt = tallies.find { it.optionId == opt.id }?.count ?: 0
            val share = if (totalVotes > 0) cnt.toFloat() / totalVotes else 0f
            JournalPollOptionTile(
                text = opt.text,
                share = share,
                voteCount = cnt,
                selected = myVoteOptionId == opt.id,
                showVoteCount = false,
            )
        }
        if (remaining > 0) {
            Text(
                text = stringResource(R.string.team_news_poll_more_options, remaining),
                style = PremiumJournalFeedTokens.metaStyle,
            )
        }
    }
}
