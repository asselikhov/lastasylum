package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.update.fetchNewerApkDownloadUrl
import com.lastasylum.alliance.update.openApkDownload
import com.lastasylum.alliance.update.toastNoUpdateAvailable
import com.lastasylum.alliance.update.toastUpdateCheckFailed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    username: String,
    role: String,
) {
    val quietMode = remember { mutableStateOf(false) }
    val compactOverlay = remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val buildTimeStr = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(BuildConfig.BUILD_TIME_MS))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.profile_pilot, username),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )
        SettingRow(
            title = stringResource(R.string.profile_quiet_title),
            subtitle = stringResource(R.string.profile_quiet_subtitle),
            checked = quietMode.value,
            onToggle = { quietMode.value = it },
        )
        SettingRow(
            title = stringResource(R.string.profile_compact_title),
            subtitle = stringResource(R.string.profile_compact_subtitle),
            checked = compactOverlay.value,
            onToggle = { compactOverlay.value = it },
        )
        Text(
            text = stringResource(R.string.profile_role, role),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.profile_build_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(
                R.string.profile_build_line,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                buildTimeStr,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                scope.launch {
                    runCatching { fetchNewerApkDownloadUrl() }
                        .onSuccess { url ->
                            if (url != null) {
                                context.openApkDownload(url)
                            } else {
                                context.toastNoUpdateAvailable()
                            }
                        }
                        .onFailure {
                            context.toastUpdateCheckFailed()
                        }
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.profile_check_update))
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
