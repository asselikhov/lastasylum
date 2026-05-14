package com.lastasylum.alliance.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericPurple
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericSky
import com.lastasylum.alliance.ui.theme.SquadRelayBackground
import com.lastasylum.alliance.ui.theme.SquadRelayVoidBottom

/**
 * Mood strip under squad header (no remote art yet): night void + soft violet/cyan glows.
 */
@Composable
fun TeamHeroStrip(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val bottomScrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(shape)
            .drawBehind {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SquadRelayVoidBottom,
                            SquadRelayBackground,
                            Color(0xFF12182C),
                        ),
                        startY = 0f,
                        endY = h,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SquadRelayAtmosphericPurple.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.18f, h * 0.55f),
                        radius = w * 0.75f,
                    ),
                    radius = w * 0.75f,
                    center = Offset(w * 0.18f, h * 0.55f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SquadRelayAtmosphericSky.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.88f, h * 0.25f),
                        radius = h * 0.9f,
                    ),
                    radius = h * 0.9f,
                    center = Offset(w * 0.88f, h * 0.25f),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            bottomScrim,
                        ),
                        startY = h * 0.35f,
                        endY = h,
                    ),
                )
            },
    ) {
        Box(Modifier.fillMaxSize())
    }
}
