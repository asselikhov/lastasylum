package com.lastasylum.alliance.ui.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayAwareBottomSheet
import com.lastasylum.alliance.overlay.OverlayChatImagePickerSheet
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayInteractionSuppressEffect
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import com.lastasylum.alliance.ui.util.ComposerPasteChipRow
import com.lastasylum.alliance.ui.util.composerLongPressPaste
import com.lastasylum.alliance.ui.util.rememberComposerClipboardHasText
import com.lastasylum.alliance.ui.util.rememberComposerPasteState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatComposer(
    draft: String,
    pickedImageUris: List<Uri>,
    replyToMessage: ChatMessage?,
    isSending: Boolean,
    sendEnabled: Boolean = true,
    readOnly: Boolean = false,
    canUseZlobyakaStickers: Boolean = false,
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onSendStickerPayload: (String) -> Unit,
    onPickImages: (List<Uri>, append: Boolean) -> Unit,
    onRemovePickedImage: (Uri) -> Unit,
    onClearPickedImages: () -> Unit,
    onClearReply: () -> Unit,
    onOpenAttachmentPreview: (Int) -> Unit = {},
    pendingApkLabel: String? = null,
    onClearPendingApk: (() -> Unit)? = null,
    isForumAdmin: Boolean = false,
    onPickApk: (() -> Unit)? = null,
    hasReadyFileAttachment: Boolean = false,
    isUploadingFile: Boolean = false,
) {
    var showMediaPanel by remember { mutableStateOf(false) }
    var showAttachmentsSheet by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showOverlayGalleryPicker by remember { mutableStateOf(false) }
    val composerLocked = readOnly || isSending || isUploadingFile
    val pasteState = rememberComposerPasteState(
        readOnly = composerLocked,
        draft = draft,
        onDraftChange = onDraftChange,
    )
    val clipboardHasText = rememberComposerClipboardHasText()
    val context = LocalContext.current
    val overlayUi = LocalOverlayUiMode.current
    val activityResultOwner = LocalActivityResultRegistryOwner.current
    val canHandleBack = LocalOnBackPressedDispatcherOwner.current != null
    val zlobStems = remember(context) { ZlobyakaStickerPack.listSortedStems(context) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val pickImagesLauncher = if (activityResultOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
            onResult = { uris ->
                if (overlayUi) {
                    OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
                }
                if (!readOnly && uris.isNotEmpty()) {
                    onPickImages(uris, pickedImageUris.isNotEmpty())
                    if (overlayUi) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.chat_attachments_added, uris.size),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    } else {
        null
    }
    if (canHandleBack) {
        BackHandler(enabled = showMediaPanel) {
            showMediaPanel = false
        }
    }

    if (canHandleBack) {
        BackHandler(enabled = showAttachmentsSheet) {
            showAttachmentsSheet = false
        }
    }

    if (canHandleBack) {
        BackHandler(enabled = showOverlayGalleryPicker) {
            showOverlayGalleryPicker = false
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
        }
    }

    if (overlayUi && showOverlayGalleryPicker) {
        OverlayChatImagePickerSheet(
            maxSelection = 12,
            onDismiss = {
                showOverlayGalleryPicker = false
                OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
            },
            onConfirm = { uris ->
                if (!composerLocked && uris.isNotEmpty()) {
                    onPickImages(uris, pickedImageUris.isNotEmpty())
                }
            },
        )
    }

    if (showAttachmentsSheet) {
        OverlayModalScope {
        OverlayAwareBottomSheet(onDismissRequest = { showAttachmentsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SquadRelayDimens.contentPaddingHorizontal,
                        vertical = SquadRelayDimens.itemGap,
                    ),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chat_attachments_sheet_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        onClick = onClearPickedImages,
                        enabled = !readOnly && !isSending,
                    ) {
                        Text(stringResource(R.string.chat_attachments_clear))
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 92.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pickedImageUris, key = { it.toString() }) { uri ->
                        val sheetScheme = MaterialTheme.colorScheme
                        val thumbShape = RoundedCornerShape(14.dp)
                        Surface(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable {
                                    pickedImageUris.indexOf(uri).takeIf { it >= 0 }?.let(onOpenAttachmentPreview)
                                },
                            shape = thumbShape,
                            color = sheetScheme.surface.copy(alpha = 0.42f),
                            border = BorderStroke(
                                1.dp,
                                sheetScheme.outlineVariant.copy(alpha = 0.28f),
                            ),
                            tonalElevation = 0.dp,
                            shadowElevation = 2.dp,
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = SquadRelayImageRequests.localUriPreview(context, uri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                IconButton(
                                    onClick = { onRemovePickedImage(uri) },
                                    enabled = !readOnly && !isSending,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(32.dp)
                                        .padding(4.dp),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.38f),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Cancel,
                                            contentDescription = stringResource(R.string.chat_attachments_remove),
                                            tint = Color.White,
                                            modifier = Modifier.padding(5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SquadRelayDimens.itemGap),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 8.dp,
                    bottom = if (showMediaPanel) 2.dp else 0.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
        ) {
                pendingApkLabel?.let { apkName ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                        shape = RoundedCornerShape(20.dp),
                        color = SquadRelaySurfaces.subtleColor(0.48f),
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp,
                        border = SquadRelaySurfaces.panelBorder(),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = SquadRelayDimens.itemGap,
                                vertical = SquadRelayDimens.itemGap,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.chat_picked_apk_label, apkName),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                            )
                            TextButton(
                                onClick = { onClearPendingApk?.invoke() },
                                enabled = !composerLocked,
                            ) {
                                Text(stringResource(R.string.chat_picked_apk_remove))
                            }
                        }
                    }
                }
                if (pickedImageUris.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_attachments_label, pickedImageUris.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onClearPickedImages,
                            enabled = !readOnly && !isSending,
                        ) {
                            Text(stringResource(R.string.chat_attachments_clear))
                        }
                    }
                    // Telegram-like: grid preview with +N overlay; manage/remove in the sheet.
                    val maxShown = 4
                    val shown = pickedImageUris.take(maxShown)
                    val extraCount = (pickedImageUris.size - shown.size).coerceAtLeast(0)
                    val gap = 6.dp
                    val tileShape = RoundedCornerShape(14.dp)
                    val tileBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)

                    fun openAt(uri: Uri) {
                        pickedImageUris.indexOf(uri).takeIf { it >= 0 }?.let(onOpenAttachmentPreview)
                    }

                    @Composable
                    fun tile(uri: Uri, modifier: Modifier, isExtraTile: Boolean) {
                        val scheme = MaterialTheme.colorScheme
                        Surface(
                            modifier = modifier.clickable {
                                if (isExtraTile) showAttachmentsSheet = true else openAt(uri)
                            },
                            shape = tileShape,
                            color = tileBg,
                            border = BorderStroke(
                                1.dp,
                                scheme.outlineVariant.copy(alpha = 0.28f),
                            ),
                            tonalElevation = 0.dp,
                            shadowElevation = 2.dp,
                        ) {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = SquadRelayImageRequests.localUriPreview(context, uri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                if (isExtraTile && extraCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "+$extraCount",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    when (shown.size) {
                        1 -> {
                            tile(
                                uri = shown.first(),
                                modifier = Modifier
                                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal)
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                isExtraTile = false,
                            )
                        }
                        2 -> {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal)
                                    .fillMaxWidth()
                                    .height(150.dp),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                tile(shown[0], Modifier.weight(1f).fillMaxHeight(), isExtraTile = false)
                                tile(shown[1], Modifier.weight(1f).fillMaxHeight(), isExtraTile = extraCount > 0)
                            }
                        }
                        3 -> {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal)
                                    .fillMaxWidth()
                                    .height(180.dp),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                tile(shown[0], Modifier.weight(1.25f).fillMaxHeight(), isExtraTile = false)
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(gap),
                                ) {
                                    tile(shown[1], Modifier.weight(1f).fillMaxWidth(), isExtraTile = false)
                                    tile(shown[2], Modifier.weight(1f).fillMaxWidth(), isExtraTile = extraCount > 0)
                                }
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal)
                                    .fillMaxWidth()
                                    .height(190.dp),
                                verticalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                ) {
                                    tile(shown[0], Modifier.weight(1f).fillMaxHeight(), isExtraTile = false)
                                    tile(shown[1], Modifier.weight(1f).fillMaxHeight(), isExtraTile = false)
                                }
                                Row(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                ) {
                                    tile(shown[2], Modifier.weight(1f).fillMaxHeight(), isExtraTile = false)
                                    tile(shown[3], Modifier.weight(1f).fillMaxHeight(), isExtraTile = extraCount > 0)
                                }
                            }
                        }
                    }
                }
                replyToMessage?.let { reply ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SquadRelaySurfaces.subtleColor(0.48f),
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp,
                        border = SquadRelaySurfaces.panelBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = SquadRelayDimens.itemGap,
                                vertical = SquadRelayDimens.itemGap,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        R.string.chat_replying_to,
                                        chatSenderDisplayWithTag(reply.senderTeamTag, reply.senderUsername, reply.senderServerNumber),
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = replyPreviewText(reply.text),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = onClearReply) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.chat_reply_cancel),
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    ComposerPasteChipRow(
                        state = pasteState,
                        canHandleBack = canHandleBack,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = SquadRelaySurfaces.panelColor(),
                        border = SquadRelaySurfaces.panelBorder(),
                        tonalElevation = 0.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = SquadRelayDimens.composerMinHeight.coerceAtLeast(48.dp))
                            .composerLongPressPaste(
                                enabled = !composerLocked,
                                onLongPress = pasteState.onLongPress,
                            ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    if (readOnly) return@IconButton
                                    if (showMediaPanel) {
                                        showMediaPanel = false
                                        focusRequester.requestFocus()
                                        keyboard?.show()
                                    } else {
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                        showMediaPanel = true
                                    }
                                },
                                enabled = !readOnly,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = if (showMediaPanel) {
                                        Icons.Outlined.Keyboard
                                    } else {
                                        Icons.Outlined.Mood
                                    },
                                    contentDescription = if (showMediaPanel) {
                                        stringResource(R.string.chat_show_keyboard_cd)
                                    } else {
                                        stringResource(R.string.chat_open_media_panel_cd)
                                    },
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (clipboardHasText) {
                                IconButton(
                                    onClick = { pasteState.onPaste() },
                                    enabled = !composerLocked,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentPaste,
                                        contentDescription = stringResource(
                                            R.string.chat_composer_paste_label,
                                        ),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .composerLongPressPaste(
                                        enabled = !readOnly,
                                        onLongPress = pasteState.onLongPress,
                                    ),
                            ) {
                                TextField(
                                    value = draft,
                                    onValueChange = { if (!readOnly) onDraftChange(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .composerLongPressPaste(
                                            enabled = !readOnly,
                                            onLongPress = pasteState.onLongPress,
                                        )
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { fc ->
                                            if (!readOnly && fc.isFocused) {
                                                if (showMediaPanel) {
                                                    showMediaPanel = false
                                                    keyboard?.show()
                                                }
                                            } else if (!fc.isFocused) {
                                                pasteState.onDismissMenu()
                                            }
                                        },
                                    readOnly = readOnly,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    placeholder = {
                                        Text(
                                            text = if (pickedImageUris.isNotEmpty()) {
                                                stringResource(R.string.chat_caption_hint)
                                            } else {
                                                stringResource(R.string.chat_message_hint)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        keyboardType = KeyboardType.Text,
                                    ),
                                    maxLines = 6,
                                )
                            }
                            val canSend = sendEnabled &&
                                !composerLocked &&
                                (
                                    draft.isNotBlank() ||
                                        pickedImageUris.isNotEmpty() ||
                                        hasReadyFileAttachment
                                    )
                            val sendButtonEnabled = canSend && !isSending
                            val showSendButton = canSend || isSending

                            fun openImageAttach() {
                                if (composerLocked) return
                                focusManager.clearFocus()
                                keyboard?.hide()
                                OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                if (overlayUi) {
                                    showOverlayGalleryPicker = true
                                } else {
                                    val launcher = pickImagesLauncher
                                    if (launcher == null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.chat_attachment_read_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        launcher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                                            ),
                                        )
                                    }
                                }
                            }

                            if (isForumAdmin && onPickApk != null) {
                                Box {
                                    IconButton(
                                        onClick = { showAttachMenu = true },
                                        enabled = !composerLocked,
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AttachFile,
                                            contentDescription = stringResource(R.string.chat_attach_apk_cd),
                                            tint = if (!composerLocked) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                            },
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showAttachMenu,
                                        onDismissRequest = { showAttachMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.chat_attach_menu_photo)) },
                                            onClick = {
                                                showAttachMenu = false
                                                openImageAttach()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.chat_attach_menu_apk)) },
                                            onClick = {
                                                showAttachMenu = false
                                                onPickApk()
                                            },
                                        )
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = { openImageAttach() },
                                    enabled = !composerLocked,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AttachFile,
                                        contentDescription = stringResource(R.string.chat_attach_images_cd),
                                        tint = if (!composerLocked) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        },
                                    )
                                }
                            }

                            if (showSendButton) {
                                IconButton(
                                    onClick = { if (sendButtonEnabled) onSendDraft() },
                                    enabled = sendButtonEnabled || isSending,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    if (isSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Send,
                                            contentDescription = stringResource(R.string.chat_send),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

        // РљРѕСЂРѕС‚РєРёР№ fade Р±РµР· РёР·РјРµРЅРµРЅРёСЏ РІС‹СЃРѕС‚С‹ вЂ” РјРµРЅСЊС€Рµ РєРѕРЅС„Р»РёРєС‚РѕРІ СЃ IME РїСЂРё РєР»Р°РІРёР°С‚СѓСЂРµ.
        AnimatedVisibility(
            visible = showMediaPanel,
            enter = fadeIn(animationSpec = tween(90)),
            exit = fadeOut(animationSpec = tween(70)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
            ) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    Text(
                        text = stringResource(R.string.chat_stickers_pack_zlobyaka),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(zlobStems, key = { it }) { stem ->
                            val sch = MaterialTheme.colorScheme
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = sch.surface.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    sch.outlineVariant.copy(alpha = 0.28f),
                                ),
                                tonalElevation = 0.dp,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .aspectRatio(1f)
                                    .clickable(
                                        enabled = sendEnabled &&
                                            !readOnly &&
                                            canUseZlobyakaStickers,
                                        onClick = {
                                            onSendStickerPayload(ZlobyakaStickerPack.encode(stem))
                                            showMediaPanel = false
                                        },
                                    ),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(ZlobyakaStickerPack.assetUriForStem(stem))
                                            .size(192)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = stringResource(R.string.cd_chat_sticker),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit,
                                        alpha = if (canUseZlobyakaStickers) 1f else 0.42f,
                                    )
                                    if (!canUseZlobyakaStickers) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(
                                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f),
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Lock,
                                                contentDescription = stringResource(
                                                    R.string.cd_chat_sticker_locked,
                                                ),
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(26.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AttachmentPreviewOverlay(
    modifier: Modifier = Modifier,
    uris: List<Uri>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onOpenExternal: (Uri) -> Unit,
    onRemove: (Uri) -> Unit,
) {
    if (uris.isEmpty()) return
    var index by remember(startIndex, uris) {
        mutableStateOf(startIndex.coerceIn(0, uris.lastIndex))
    }
    val uri = uris.getOrNull(index) ?: uris.first()

    var scale by remember(uri) { mutableStateOf(1f) }
    var offsetX by remember(uri) { mutableStateOf(0f) }
    var offsetY by remember(uri) { mutableStateOf(0f) }
    val context = LocalContext.current

    OverlayInteractionSuppressEffect()
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        fun clampOffsets() {
            if (scale <= 1f) {
                offsetX = 0f
                offsetY = 0f
                return
            }
            val maxX = (wPx * (scale - 1f) / 2f).coerceAtLeast(0f)
            val maxY = (hPx * (scale - 1f) / 2f).coerceAtLeast(0f)
            offsetX = offsetX.coerceIn(-maxX, maxX)
            offsetY = offsetY.coerceIn(-maxY, maxY)
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
            scale = nextScale
            if (scale > 1f) {
                offsetX += panChange.x
                offsetY += panChange.y
            }
            clampOffsets()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uris, index, scale) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            clampOffsets()
                        },
                    )
                    detectHorizontalDragGestures(
                        onDragEnd = { /* noop */ },
                        onHorizontalDrag = { change, dragAmount ->
                            if (scale > 1f) return@detectHorizontalDragGestures
                            change.consume()
                            if (kotlin.math.abs(dragAmount) < 14f) return@detectHorizontalDragGestures
                            if (dragAmount > 0 && index > 0) index -= 1
                            if (dragAmount < 0 && index < uris.lastIndex) index += 1
                        },
                    )
                }
                .transformable(state = transformState),
        ) {
                AsyncImage(
                    model = SquadRelayImageRequests.localUriPreview(context, uri),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    contentScale = ContentScale.Fit,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Text(
                    text = "${index + 1}/${uris.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { onOpenExternal(uri) }) {
                        Text(
                            text = stringResource(R.string.chat_attachments_open),
                            color = Color.White,
                        )
                    }
                    TextButton(onClick = { onRemove(uri) }) {
                        Text(
                            text = stringResource(R.string.chat_attachments_remove),
                            color = Color.White,
                        )
                    }
                }
            }

            if (uris.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uris, key = { it.toString() }) { u ->
                        val i = uris.indexOf(u)
                        val isActive = i == index
                        val border = if (isActive) BorderStroke(2.dp, Color.White) else null
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = border,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            color = Color.Transparent,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    index = i
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                },
                        ) {
                            AsyncImage(
                                model = SquadRelayImageRequests.localUriPreview(context, u),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
}
