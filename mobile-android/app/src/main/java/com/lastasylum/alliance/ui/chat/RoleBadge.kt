package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.theme.roleOnAccentColor

@Composable
fun RoleBadge(
    role: String,
    modifier: Modifier = Modifier,
) {
    val bg = roleAccentColor(role)
    val fg = roleOnAccentColor(role)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = bg.copy(alpha = 0.9f),
    ) {
        Text(
            text = role,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = fg,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.25.sp,
            ),
        )
    }
}
