package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.chat.ChatState

@Composable
fun ChatScreen(
    contentPadding: PaddingValues,
    username: String,
    role: String,
    state: ChatState,
    onSendMessage: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.chat_logged_in, username, role),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = stringResource(R.string.chat_voice_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            if (state.messages.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.messages.forEach { message ->
                    ChatCard(
                        senderRole = message.senderRole,
                        senderUsername = message.senderUsername,
                        message = message.text,
                    )
                }
            }
        }
        if (!state.error.isNullOrBlank()) {
            Text(text = state.error, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.chat_message_hint)) },
                singleLine = true,
            )
            TextButton(
                onClick = {
                    onSendMessage(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) {
                Text(stringResource(R.string.chat_send))
            }
        }
    }
}

@Composable
private fun ChatCard(senderRole: String, senderUsername: String, message: String) {
    val sender = stringResource(R.string.chat_message_header, senderRole, senderUsername)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = sender,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
