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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.data.chat.stickers.ChatStickerPack
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
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
import com.lastasylum.alliance.ui.util.rememberComposerPasteState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatComposer(
    draft: String,
    pickedImageUris: List<Uri>,
    replyToMessage: ChatMessage?,
    editingMessage: ChatMessage? = null,
    isSending: Boolean,
    sendEnabled: Boolean = true,
    readOnly: Boolean = false,
    allowMediaAttachments: Boolean = true,
    enabledStickerPackKeys: Set<String> = emptySet(),
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onSendStickerPayload: (String) -> Unit,
    onPickImages: (List<Uri>, append: Boolean) -> Unit,
    onRemovePickedImage: (Uri) -> Unit,
    onClearPickedImages: () -> Unit,
    onClearReply: () -> Unit,
    onClearEdit: () -> Unit = {},
    onOpenAttachmentPreview: (Int) -> Unit = {},
    pendingApkLabel: String? = null,
    onClearPendingApk: (() -> Unit)? = null,
    onPickApk: (() -> Unit)? = null,
    hasReadyFileAttachment: Boolean = false,
    isUploadingFile: Boolean = false,
) {
    var showMediaPanel by remember { mutableStateOf(false) }
    var selectedStickerPackKey by remember { mutableStateOf<String?>(null) }
    var showAttachmentsSheet by remember { mutableStateOf(false) }
    var showOverlayGalleryPicker by remember { mutableStateOf(false) }
    val composerLocked = readOnly || isSending || isUploadingFile
    val isEditing = editingMessage != null
    val effectiveAllowMedia = allowMediaAttachments && !isEditing
    val pasteState = rememberComposerPasteState(
        readOnly = composerLocked,
        draft = draft,
        onDraftChange = onDraftChange,
    )
    val context = LocalContext.current
    val overlayUi = LocalOverlayUiMode.current
    val activityResultOwner = LocalActivityResultRegistryOwner.current
    val canHandleBack = LocalOnBackPressedDispatcherOwner.current != null
    val enabledStickerPacks = remember(enabledStickerPackKeys, context) {
        StickerPacks.enabledPacks(enabledStickerPackKeys)
    }
    val hasStickerPacks = effectiveAllowMedia && enabledStickerPacks.isNotEmpty()
    LaunchedEffect(allowMediaAttachments, isEditing) {
        if (!effectiveAllowMedia) {
            showMediaPanel = false
        }
    }
    LaunchedEffect(showMediaPanel, enabledStickerPacks.map { it.packKey }) {
        if (!showMediaPanel) return@LaunchedEffect
        val keys = enabledStickerPacks.map { it.packKey }
        if (keys.isEmpty()) {
            selectedStickerPackKey = null
            return@LaunchedEffect
        }
        if (selectedStickerPackKey !in keys) {
            selectedStickerPackKey = keys.first()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val pickImagesLauncher = if (activityResultOwner != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
            onResult = { uris ->
                showOverlayGalleryPicker = false
                if (overlayUi) {
                    OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
                }
                if (!readOnly && uris.isNotEmpty()) {
                    val stable = stabilizeComposerImageUris(context, uris)
                    if (stable.isEmpty()) return@rememberLauncherForActivityResult
                    onPickImages(stable, pickedImageUris.isNotEmpty())
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

    // ???????: ????? ?????????? ?????? ???????? ???????? ????? applyOverlayPickedUris ??? onConfirm sheet.
    LaunchedEffect(pickedImageUris.size, showOverlayGalleryPicker) {
        if (showOverlayGalleryPicker && pickedImageUris.isNotEmpty()) {
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
                showOverlayGalleryPicker = false
                if (!composerLocked && uris.isNotEmpty()) {
                    val stable = stabilizeComposerImageUris(context, uris)
                    if (stable.isNotEmpty()) {
                        onPickImages(stable, pickedImageUris.isNotEmpty())
                    }
                }
                OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
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
                    if (!allowMediaAttachments) return@let
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
                if (allowMediaAttachments && pickedImageUris.isNotEmpty()) {
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
                editingMessage?.let { editing ->
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
                                    text = stringResource(R.string.chat_editing_message),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = replyPreviewText(editing.text),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = onClearEdit) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.chat_edit_cancel),
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
                            if (hasStickerPacks) {
                                IconButton(
                                    onClick = {
                                        if (readOnly) return@IconButton
                                        if (showMediaPanel) {
                                            showMediaPanel = false
                                            focusManager.clearFocus()
                                            keyboard?.hide()
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
                                                }
                                            } else if (!fc.isFocused) {
                                                pasteState.onDismissMenu()
                                            }
                                        },
                                    readOnly = readOnly,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    placeholder = {
                                        Text(
                                            text = when {
                                                isEditing -> stringResource(R.string.chat_editing_message)
                                                pickedImageUris.isNotEmpty() -> stringResource(R.string.chat_caption_hint)
                                                else -> stringResource(R.string.chat_message_hint)
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
                                        isEditing ||
                                        (effectiveAllowMedia && pickedImageUris.isNotEmpty()) ||
                                        (effectiveAllowMedia && hasReadyFileAttachment)
                                    )
                            val sendButtonEnabled = canSend && !isSending
                            val showSendButton = canSend || isSending

                            fun openImageAttach() {
                                if (composerLocked) return
                                focusManager.clearFocus()
                                keyboard?.hide()
                                OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                val launcher = pickImagesLauncher
                                if (launcher == null) {
                                    if (overlayUi) {
                                        showOverlayGalleryPicker = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.chat_attachment_read_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(
                                            overlayUi,
                                        )
                                    }
                                    return
                                }
                                launcher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            }

                            if (effectiveAllowMedia) {
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
                            if (effectiveAllowMedia && onPickApk != null) {
                                IconButton(
                                    onClick = {
                                        if (composerLocked) return@IconButton
                                        onPickApk()
                                    },
                                    enabled = !composerLocked,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AttachFile,
                                        contentDescription = stringResource(R.string.chat_attach_apk_cd),
                                        tint = if (!composerLocked) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        },
                                    )
                                }
                            }

                            if (showSendButton) {
                                IconButton(
                                    onClick = {
                                        if (!sendButtonEnabled) return@IconButton
                                        onSendDraft()
                                    },
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
                                            contentDescription = if (isEditing) {
                                                stringResource(R.string.chat_edit_save_cd)
                                            } else {
                                                stringResource(R.string.chat_send)
                                            },
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

        // ??? ???????? ?????? ? ?????? ?????????? ? IME ??? ???????? ??????????.
        AnimatedVisibility(
            visible = showMediaPanel,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (overlayUi) {
                            Modifier.navigationBarsPadding()
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
            ) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                StickerPackPickerPanel(
                    packs = enabledStickerPacks,
                    selectedPackKey = selectedStickerPackKey,
                    onSelectPackKey = { selectedStickerPackKey = it },
                    context = context,
                    sendEnabled = sendEnabled,
                    readOnly = readOnly,
                    onSendStickerPayload = { payload ->
                        onSendStickerPayload(payload)
                        showMediaPanel = false
                    },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StickerPackPickerPanel(
    packs: List<ChatStickerPack>,
    selectedPackKey: String?,
    onSelectPackKey: (String) -> Unit,
    context: android.content.Context,
    sendEnabled: Boolean,
    readOnly: Boolean,
    onSendStickerPayload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (packs.isEmpty()) return
    val activePack = packs.find { it.packKey == selectedPackKey } ?: packs.first()
    Column(modifier = modifier.fillMaxWidth()) {
        if (packs.size > 1) {
            val tabIndex = packs.indexOfFirst { it.packKey == activePack.packKey }.coerceAtLeast(0)
            PrimaryScrollableTabRow(
                selectedTabIndex = tabIndex,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                divider = {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                },
            ) {
                packs.forEach { pack ->
                    Tab(
                        selected = pack.packKey == activePack.packKey,
                        onClick = { onSelectPackKey(pack.packKey) },
                        text = {
                            Text(
                                text = stringResource(pack.titleRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
        StickerPackStickerGrid(
            pack = activePack,
            context = context,
            sendEnabled = sendEnabled,
            readOnly = readOnly,
            onSendStickerPayload = onSendStickerPayload,
        )
    }
}

@Composable
private fun StickerPackStickerGrid(
    pack: ChatStickerPack,
    context: android.content.Context,
    sendEnabled: Boolean,
    readOnly: Boolean,
    onSendStickerPayload: (String) -> Unit,
) {
    val stems = remember(pack.packKey, context) { pack.listStems(context) }
    if (stems.isEmpty()) return
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(stems, key = { "${pack.packKey}:$it" }) { stem ->
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
                        enabled = sendEnabled && !readOnly,
                        onClick = { onSendStickerPayload(pack.encode(stem)) },
                    ),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(pack.assetUriForStem(stem))
                        .size(192)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_chat_sticker),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
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
