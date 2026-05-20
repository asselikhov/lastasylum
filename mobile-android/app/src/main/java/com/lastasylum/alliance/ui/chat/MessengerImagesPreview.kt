package com.lastasylum.alliance.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayInteractionSuppressEffect

/**
 * Telegram-style full-screen image viewer: separate window in the app, overlay layer in-game.
 */
@Composable
fun MessengerImagesPreviewHost(
    urls: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    val overlayUi = LocalOverlayUiMode.current
    if (overlayUi) {
        MessengerImagesPreviewOverlay(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(32f),
            urls = urls,
            startIndex = startIndex,
            onDismiss = onDismiss,
        )
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            MessengerImagesPreviewOverlay(
                modifier = Modifier.fillMaxSize(),
                urls = urls,
                startIndex = startIndex,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
fun MessengerImagesPreviewOverlay(
    modifier: Modifier = Modifier,
    urls: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    var index by remember(startIndex, urls) {
        mutableStateOf(startIndex.coerceIn(0, urls.lastIndex))
    }
    val url = urls.getOrNull(index) ?: urls.first()

    var scale by remember(url) { mutableStateOf(1f) }
    var offsetX by remember(url) { mutableStateOf(0f) }
    var offsetY by remember(url) { mutableStateOf(0f) }
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
                .pointerInput(urls, index, scale) {
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
                            if (dragAmount < 0 && index < urls.lastIndex) index += 1
                        },
                    )
                }
                .transformable(state = transformState),
        ) {
            AsyncImage(
                model = chatAuthedImageRequest(context, url),
                contentDescription = stringResource(R.string.cd_chat_message_image),
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
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.chat_attachments_open),
                    tint = Color.White,
                )
            }
            Text(
                text = "${index + 1}/${urls.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        if (urls.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = urls.size,
                    key = { it },
                ) { i ->
                    val u = urls[i]
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
                            model = chatAuthedImageRequest(context, u),
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
