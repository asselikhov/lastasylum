package com.lastasylum.alliance.ui.screens.teamforum

import android.net.Uri
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayChatImagePickerSheet
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.ui.chat.replyPreviewText
import com.lastasylum.alliance.ui.screens.teamnews.teamNewsAuthedImageRequest
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/**
 * Composer row matching the main [com.lastasylum.alliance.ui.screens.ChatScreen] bar:
 * sticker / keyboard toggle, text field, image attach, send (no voice — forum has no audio API).
 */
@Composable
fun ForumTopicComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    replyTo: TeamForumMessageDto?,
    onClearReply: () -> Unit,
    pendingImageUris: List<Uri>,
    onClearPendingImage: () -> Unit,
    pendingImageRemotePreviewUrl: String?,
    /** Загруженные на сервер fileId (как во вкладке «Чат») — без них отправка только по локальным URI запрещена. */
    postedImageFileIds: List<String>,
    pendingApkLabel: String? = null,
    postedApkFileId: String? = null,
    isForumAdmin: Boolean = false,
    isSending: Boolean,
    isUploadingImage: Boolean,
    isUploadingFile: Boolean = false,
    sendEnabled: Boolean,
    canUseZlobyakaStickers: Boolean,
    onSend: () -> Unit,
    onSendStickerPayload: (String) -> Unit,
    onImageUrisPicked: (List<Uri>) -> Unit,
    onPickApk: (Uri, String) -> Unit = { _, _ -> },
    onClearPendingApk: () -> Unit = {},
    onTyping: () -> Unit,
) {
    var showMediaPanel by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showOverlayGalleryPicker by remember { mutableStateOf(false) }
    val isUploadingAttachment = isUploadingImage || isUploadingFile
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
                if (uris.isNotEmpty()) onImageUrisPicked(uris)
            },
        )
    } else null

    val pickApkLauncher = if (activityResultOwner != null && isForumAdmin) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri ->
                if (uri != null) {
                    onPickApk(uri, com.lastasylum.alliance.ui.chat.queryDisplayName(context, uri))
                }
            },
        )
    } else null

    LaunchedEffect(showMediaPanel) {
        if (!showMediaPanel) {
            // no-op; stickers-only panel
        }
    }

    fun openImageAttach() {
        focusManager.clearFocus()
        keyboard?.hide()
        OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
        if (overlayUi) {
            showOverlayGalleryPicker = true
        } else {
            pickImagesLauncher?.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    if (canHandleBack) {
        BackHandler(enabled = showMediaPanel) {
            showMediaPanel = false
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
                if (uris.isNotEmpty()) onImageUrisPicked(uris)
            },
        )
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
        val showPending = pendingImageUris.isNotEmpty() ||
            !pendingImageRemotePreviewUrl.isNullOrBlank()
        pendingApkLabel?.let { apkName ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal)
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.chat_picked_apk_label, apkName),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    TextButton(
                        onClick = onClearPendingApk,
                        enabled = !isSending && !isUploadingAttachment,
                    ) {
                        Text(stringResource(R.string.chat_picked_apk_remove))
                    }
                }
            }
        }
        if (showPending) {
            Box(
                modifier = Modifier
                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal)
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                when {
                    pendingImageUris.isNotEmpty() -> {
                        ForumLocalAttachmentsPreview(
                            uris = pendingImageUris,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        pendingImageRemotePreviewUrl?.let { raw ->
                            val req = teamNewsAuthedImageRequest(context, raw)
                            if (req != null) {
                                AsyncImage(
                                    model = req,
                                    contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
                if (isUploadingAttachment) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }
                IconButton(
                    onClick = onClearPendingImage,
                    enabled = !isSending && !isUploadingAttachment,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.38f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        tonalElevation = 0.dp,
                        shadowElevation = 1.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cancel,
                            contentDescription = stringResource(R.string.chat_attachments_remove),
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 8.dp,
                    bottom = if (showMediaPanel) 2.dp else 0.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
        ) {
            replyTo?.let { msg ->
                val scheme = MaterialTheme.colorScheme
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = scheme.surface.copy(alpha = 0.48f),
                    tonalElevation = 0.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.18f)),
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
                                    msg.senderUsername.trim().ifBlank { "—" },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                            Text(
                                text = replyPreviewText(msg.replyTo?.text ?: msg.text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = onClearReply) {
                            Icon(
                                imageVector = Icons.Outlined.Cancel,
                                contentDescription = stringResource(R.string.chat_reply_cancel),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SquadRelayDimens.composerMinHeight.coerceAtLeast(48.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
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
                            enabled = !isSending && !isUploadingAttachment,
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
                        BasicTextField(
                            value = draft,
                            onValueChange = {
                                onDraftChange(it)
                                if (it.isNotEmpty()) onTyping()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged { fc ->
                                    if (fc.isFocused && showMediaPanel) {
                                        showMediaPanel = false
                                        keyboard?.show()
                                    }
                                },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                keyboardType = KeyboardType.Text,
                            ),
                            maxLines = 6,
                            decorationBox = { inner ->
                                Box {
                                    if (draft.isBlank()) {
                                        Text(
                                            text = if (showPending || pendingApkLabel != null) {
                                                stringResource(R.string.chat_caption_hint)
                                            } else {
                                                stringResource(R.string.chat_message_hint)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        val canSend = sendEnabled &&
                            (
                                draft.isNotBlank() ||
                                    postedImageFileIds.isNotEmpty() ||
                                    !postedApkFileId.isNullOrBlank()
                                )
                        val sendButtonEnabled = canSend && !isSending && !isUploadingAttachment

                        Box {
                            IconButton(
                                onClick = {
                                    if (isForumAdmin) {
                                        showAttachMenu = true
                                    } else {
                                        openImageAttach()
                                    }
                                },
                                enabled = !isSending && !isUploadingAttachment,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = if (isForumAdmin) {
                                        stringResource(R.string.chat_attach_apk_cd)
                                    } else {
                                        stringResource(R.string.chat_attach_images_cd)
                                    },
                                    tint = if (!isSending && !isUploadingAttachment) {
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
                                        OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                                        pickApkLauncher?.launch("application/*")
                                    },
                                )
                            }
                        }

                        IconButton(
                            onClick = { if (sendButtonEnabled) onSend() },
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
                                    tint = if (canSend) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

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
                                    enabled = !isSending && !isUploadingAttachment && canUseZlobyakaStickers,
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

@Composable
private fun ForumLocalAttachmentsPreview(
    uris: List<Uri>,
    modifier: Modifier = Modifier,
) {
    if (uris.isEmpty()) return
    val maxShown = 6
    val shown = uris.take(maxShown)
    val extra = (uris.size - shown.size).coerceAtLeast(0)
    val gap = 4.dp
    val corner = 12.dp

    @Composable
    fun tile(idx: Int, modifier: Modifier) {
        val uri = shown.getOrNull(idx) ?: return
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (idx == shown.lastIndex && extra > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+$extra",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }

    when (shown.size) {
        1 -> tile(0, modifier)
        2 -> {
            Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
                tile(0, Modifier.weight(1f).fillMaxSize())
                tile(1, Modifier.weight(1f).fillMaxSize())
            }
        }
        else -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    tile(0, Modifier.weight(1f).fillMaxSize())
                    tile(1, Modifier.weight(1f).fillMaxSize())
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    tile(2, Modifier.weight(1f).fillMaxSize())
                    tile(3.coerceAtMost(shown.lastIndex), Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}
