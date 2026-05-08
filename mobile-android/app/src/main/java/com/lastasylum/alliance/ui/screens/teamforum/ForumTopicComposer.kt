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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    isSending: Boolean,
    isUploadingImage: Boolean,
    sendEnabled: Boolean,
    canUseZlobyakaStickers: Boolean,
    onSend: () -> Unit,
    onSendStickerPayload: (String) -> Unit,
    onImageUrisPicked: (List<Uri>) -> Unit,
    onTyping: () -> Unit,
) {
    var showMediaPanel by remember { mutableStateOf(false) }
    val context = LocalContext.current
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

    LaunchedEffect(showMediaPanel) {
        if (!showMediaPanel) {
            // no-op; stickers-only panel
        }
    }

    if (canHandleBack) {
        BackHandler(enabled = showMediaPanel) {
            showMediaPanel = false
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
        val showPending = pendingImageUris.isNotEmpty() || !pendingImageRemotePreviewUrl.isNullOrBlank()
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
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(pendingImageUris.first())
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
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
                if (isUploadingImage) {
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
                    enabled = !isSending && !isUploadingImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.38f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
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
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
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
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
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
                            enabled = !isSending && !isUploadingImage,
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
                                            text = if (showPending) {
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
                            (draft.isNotBlank() || pendingImageUris.isNotEmpty() || !pendingImageRemotePreviewUrl.isNullOrBlank())
                        val sendButtonEnabled = canSend && !isSending && !isUploadingImage

                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                pickImagesLauncher?.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            enabled = !isSending && !isUploadingImage,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = stringResource(R.string.chat_attach_images_cd),
                                tint = if (!isSending && !isUploadingImage) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                            )
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    enabled = sendEnabled && !isSending && !isUploadingImage &&
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
