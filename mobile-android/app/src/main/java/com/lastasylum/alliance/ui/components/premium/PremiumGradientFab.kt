package com.lastasylum.alliance.ui.components.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.theme.premium.PremiumFabShape

@Composable
fun PremiumGradientFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(PremiumFabShape)
            .background(
                Brush.horizontalGradient(
                    listOf(PremiumColors.accentPurple, PremiumColors.accentCyan),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = PremiumColors.accentCyan),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun PremiumGradientIconFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(PremiumFabShape)
            .background(
                Brush.linearGradient(
                    listOf(PremiumColors.accentPurpleDeep, PremiumColors.accentCyan),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = PremiumColors.accentCyan),
                onClick = onClick,
            )
            .padding(16.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        content()
    }
}
