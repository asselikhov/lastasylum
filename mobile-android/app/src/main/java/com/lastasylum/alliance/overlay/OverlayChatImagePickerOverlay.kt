package com.lastasylum.alliance.overlay

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Выбор фото внутри оверлей-окна чата (без [OverlaySystemDialogActivity] и без GONE панели).
 */
@Composable
fun OverlayChatImagePickerOverlay(
    maxSelection: Int,
    alreadyPicked: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val remainingSlots = (maxSelection - alreadyPicked).coerceAtLeast(0)

    var galleryUris by remember { mutableStateOf<List<Uri>?>(null) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasGalleryPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun loadGallery() {
        scope.launch {
            galleryUris = withContext(Dispatchers.IO) {
                runCatching { OverlayRecentGallery.loadRecentImageUris(context) }
                    .getOrDefault(emptyList())
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionDenied = false
            loadGallery()
        } else {
            permissionDenied = true
            galleryUris = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        OverlayChatInteractionHold.acquireGameForegroundSuppress()
        if (hasGalleryPermission()) {
            loadGallery()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            OverlayChatInteractionHold.releaseGameForegroundSuppress()
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(32f)
            .background(scheme.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.overlay_history_close_cd),
                        tint = scheme.onSurface,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.overlay_chat_picker_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.overlay_chat_picker_subtitle,
                            selected.size,
                            remainingSlots,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        if (selected.isNotEmpty()) {
                            onConfirm(selected.toList())
                        }
                        onDismiss()
                    },
                    enabled = selected.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.overlay_chat_picker_done))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
            when {
                galleryUris == null -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = scheme.primary,
                        )
                    }
                }
                permissionDenied || galleryUris.isNullOrEmpty() -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(
                                if (permissionDenied) {
                                    R.string.overlay_chat_picker_permission_denied
                                } else {
                                    R.string.overlay_chat_picker_empty
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 96.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(galleryUris.orEmpty(), key = { it.toString() }) { uri ->
                            val isSelected = uri in selected
                            val canSelect = isSelected || selected.size < remainingSlots
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) scheme.primary else scheme.outline.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable(enabled = canSelect) {
                                        selected = if (isSelected) {
                                            selected - uri
                                        } else {
                                            selected + uri
                                        }
                                    },
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f)),
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(22.dp),
                                        shape = CircleShape,
                                        color = scheme.primary,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = scheme.onPrimary,
                                            modifier = Modifier.padding(3.dp),
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
