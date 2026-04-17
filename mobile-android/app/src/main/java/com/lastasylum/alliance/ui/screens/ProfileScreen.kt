package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    username: String,
    role: String,
) {
    val quietMode = remember { mutableStateOf(false) }
    val compactOverlay = remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Profile & Preferences",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Pilot: $username",
            style = MaterialTheme.typography.titleMedium,
        )
        SettingRow(
            title = "Quiet mode",
            subtitle = "Reduce non-critical alerts",
            checked = quietMode.value,
            onToggle = { quietMode.value = it },
        )
        SettingRow(
            title = "Compact overlay",
            subtitle = "Smaller floating controls while in battle",
            checked = compactOverlay.value,
            onToggle = { compactOverlay.value = it },
        )
        Text(
            text = "Role: $role",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
