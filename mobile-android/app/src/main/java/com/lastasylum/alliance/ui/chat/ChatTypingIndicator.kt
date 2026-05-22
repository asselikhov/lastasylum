package com.lastasylum.alliance.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import androidx.compose.ui.res.stringResource

@Composable
fun ChatTypingIndicator(
    typingPeers: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    if (typingPeers.isEmpty()) return
    val names = typingPeers.values.distinct().sorted()
    val label = if (names.size == 1) {
        stringResource(R.string.chat_typing_one, names.first())
    } else {
        stringResource(R.string.chat_typing_many, names.joinToString(", "))
    }
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = scheme.surface.copy(alpha = 0.92f),
        tonalElevation = 1.dp,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            scheme.primary.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatTypingDots()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChatTypingDots() {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, easing = LinearEasing, delayMillis = index * 140),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_$index",
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .alpha(alpha)
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = scheme.primary,
                    modifier = Modifier.size(5.dp),
                ) {}
            }
        }
    }
}
