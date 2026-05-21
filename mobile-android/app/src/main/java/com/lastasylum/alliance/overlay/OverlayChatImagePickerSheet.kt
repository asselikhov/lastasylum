package com.lastasylum.alliance.overlay

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.chat.SquadRelayImageRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Галерея внутри окна оверлея (поверх чата).
 * Системный Photo Picker рисуется под TYPE_APPLICATION_OVERLAY — здесь MediaStore в Compose.
 */
@Composable
fun OverlayChatImagePickerSheet(
    maxSelection: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var galleryUris by remember { mutableStateOf<List<Uri>?>(null) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var loading by remember { mutableStateOf(true) }
    var permissionEpoch by remember { mutableIntStateOf(0) }

    val readPermissions = remember { OverlayDeviceGallery.requiredReadPermissions() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        if (OverlayDeviceGallery.hasReadPermission(context)) {
            galleryUris = null
            loading = true
            permissionEpoch++
        }
    }

    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxSelection),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val stable = OverlayPickedImages.copyToCache(context, uris)
            if (stable.isNotEmpty()) {
                onConfirm(stable)
                onDismiss()
            }
        } else {
            galleryUris = null
            loading = true
            permissionEpoch++
        }
    }

    LaunchedEffect(loading, permissionEpoch) {
        if (!loading) return@LaunchedEffect
        if (!OverlayDeviceGallery.hasReadPermission(context)) {
            galleryUris = null
            loading = false
            return@LaunchedEffect
        }
        galleryUris = withContext(Dispatchers.IO) {
            OverlayDeviceGallery.loadRecentImageUris(context)
        }
        loading = false
    }

    OverlayModalScope(preparedByCaller = true) {
        OverlayInWindowGalleryPicker(
            onDismissRequest = onDismiss,
            sheetMaxHeight = screenHeight * 0.9f,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.overlay_chat_gallery_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.overlay_chat_gallery_cancel))
                }
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
                !OverlayDeviceGallery.hasReadPermission(context) -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.overlay_chat_gallery_permission_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = { permissionLauncher.launch(readPermissions) }) {
                            Text(stringResource(R.string.overlay_chat_gallery_grant_access))
                        }
                    }
                }
                galleryUris.isNullOrEmpty() -> {
                    Text(
                        text = stringResource(R.string.overlay_chat_gallery_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    OutlinedButton(
                        onClick = {
                            systemPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.overlay_chat_gallery_open_system_picker))
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 96.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = screenHeight * 0.62f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(galleryUris.orEmpty(), key = { it.toString() }) { uri ->
                            val picked = uri in selected
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selected = if (picked) {
                                            selected - uri
                                        } else if (selected.size < maxSelection) {
                                            selected + uri
                                        } else {
                                            selected
                                        }
                                    }
                                    .then(
                                        if (picked) {
                                            Modifier.border(
                                                2.5.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(10.dp),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                AsyncImage(
                                    model = SquadRelayImageRequests.localUriPreview(
                                        context.applicationContext,
                                        uri,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                if (picked) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(22.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val picked = selected.toList()
                    val stable = if (picked.isEmpty()) {
                        emptyList()
                    } else {
                        OverlayPickedImages.copyToCache(context, picked)
                    }
                    onConfirm(stable)
                    onDismiss()
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(
                        R.string.overlay_chat_gallery_add_count,
                        selected.size,
                    ),
                )
            }
        }
    }
}
