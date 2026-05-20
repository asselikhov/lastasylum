package com.lastasylum.alliance.ui.screens.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

@Composable
fun AdminTeamBrandingDialog(
    initialTag: String,
    initialDisplayName: String,
    onDismiss: () -> Unit,
    onSave: (tag: String, displayName: String) -> Unit,
) {
    var tagDraft by remember(initialTag) { mutableStateOf(initialTag) }
    var nameDraft by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
    val tagOk = tagDraft.trim().length in 3..4 && tagDraft.all { it.isLetter() }
    val nameOk = nameDraft.trim().length >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SquadRelaySurfaces.dialogColor(),
        title = { Text(stringResource(R.string.admin_team_edit_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = tagDraft,
                    onValueChange = { tagDraft = it.filter { c -> c.isLetter() }.take(4).uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.admin_team_edit_tag)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.admin_team_edit_name)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(tagDraft.trim().uppercase(), nameDraft.trim()) },
                enabled = tagOk && nameOk,
            ) { Text(stringResource(R.string.admin_save_nickname)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.admin_delete_cancel))
            }
        },
    )
}
