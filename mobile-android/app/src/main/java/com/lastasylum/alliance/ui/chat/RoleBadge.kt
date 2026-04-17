package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
    Text(
        text = role,
        modifier = modifier
            .background(bg, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = fg,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        ),
    )
}
