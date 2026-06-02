package com.lastasylum.alliance.overlay

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R

@Composable
fun OverlayMarkAllReadConfirmDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = stringResource(R.string.overlay_mark_all_read_confirm_action),
    cancelLabel: String = stringResource(R.string.overlay_mark_all_read_confirm_cancel),
) {
    OverlayAwareAlertDialog(
        onDismissRequest = {
            onDismissRequest()
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
                    onConfirm()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
                },
            ) {
                Text(cancelLabel)
            }
        },
    )
}
