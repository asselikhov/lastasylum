package com.lastasylum.alliance.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex

/**
 * Host для sheet «Кто голосовал» на уровне всего экрана оверлея (не внутри карточки опроса).
 */
@Composable
fun OverlayPollVotersSheetHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var sheetRequest by remember { mutableStateOf<OverlayPollVotersRequest?>(null) }
    val showSheet: (OverlayPollVotersRequest) -> Unit = { request ->
        OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
        sheetRequest = request
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalShowOverlayPollVotersSheet provides showSheet,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            sheetRequest?.let { request ->
                OverlayModalScope(preparedByCaller = true) {
                    OverlayAwareBottomSheet(onDismissRequest = { sheetRequest = null }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.team_news_poll_voters_sheet_title,
                                    request.optionText,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            com.lastasylum.alliance.ui.screens.teamnews.TeamNewsPollVoterChips(
                                voters = request.voters,
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet that stays inside the overlay window on [LocalOverlayUiMode].
 * [ModalBottomSheet] opens a platform Dialog and on some OEM ROMs detaches overlay windows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayAwareBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalOverlayUiMode.current) {
        OverlayInWindowBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content,
        )
    } else {
        ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, content = content)
    }
}

/**
 * Почти полноэкранный sheet поверх оверлея (z-index выше чата) — для выбора фото.
 */
@Composable
fun OverlayInWindowGalleryPicker(
    onDismissRequest: () -> Unit,
    sheetMaxHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OverlayInteractionSuppressEffect()
    BackHandler(onBack = onDismissRequest)
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(40f),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun OverlayInWindowBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OverlayInteractionSuppressEffect()
    BackHandler(onBack = onDismissRequest)
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(24f),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                content = content,
            )
        }
    }
}

/**
 * Alert dialog hosted in the overlay Compose tree (no separate platform window).
 */
@Composable
fun OverlayAwareAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    if (LocalOverlayUiMode.current) {
        OverlayInteractionSuppressEffect()
        BackHandler(onBack = onDismissRequest)
        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(26f),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 400.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    title?.invoke()
                    text?.invoke()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    } else {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    }
}

/**
 * Full-screen dialog (e.g. role picker) without spawning a separate window in overlay mode.
 */
@Composable
fun OverlayAwareDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    if (LocalOverlayUiMode.current) {
        OverlayInteractionSuppressEffect()
        BackHandler(onBack = onDismissRequest)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(26f),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                content()
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties,
            content = content,
        )
    }
}
