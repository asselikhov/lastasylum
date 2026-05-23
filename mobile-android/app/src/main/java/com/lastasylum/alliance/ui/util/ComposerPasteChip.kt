package com.lastasylum.alliance.ui.util

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

@Composable
fun rememberComposerPasteState(
    readOnly: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
): ComposerPasteState {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val currentDraft by rememberUpdatedState(draft)
    val hasClipboard = remember(showMenu) {
        readClipboardPlainText(context) != null
    }
    return ComposerPasteState(
        showMenu = showMenu,
        hasClipboard = hasClipboard,
        onLongPress = {
            if (!readOnly) showMenu = true
        },
        onDismissMenu = { showMenu = false },
        onPaste = {
            val clip = readClipboardPlainText(context)
            if (clip.isNullOrBlank()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.chat_composer_paste_empty),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                onDraftChange(appendTextToDraft(currentDraft, clip))
            }
            showMenu = false
        },
    )
}

data class ComposerPasteState(
    val showMenu: Boolean,
    val hasClipboard: Boolean,
    val onLongPress: () -> Unit,
    val onDismissMenu: () -> Unit,
    val onPaste: () -> Unit,
)

@Composable
fun ComposerPasteChipRow(
    state: ComposerPasteState,
    canHandleBack: Boolean,
    modifier: Modifier = Modifier,
) {
    if (canHandleBack) {
        BackHandler(enabled = state.showMenu) {
            state.onDismissMenu()
        }
    }
    AnimatedVisibility(visible = state.showMenu) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = state.onPaste,
                enabled = state.hasClipboard,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp,
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = stringResource(R.string.chat_composer_paste_label),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(
                        alpha = if (state.hasClipboard) 1f else 0.45f,
                    ),
                )
            }
        }
    }
}
