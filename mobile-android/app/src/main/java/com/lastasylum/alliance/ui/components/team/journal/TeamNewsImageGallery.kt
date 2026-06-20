package com.lastasylum.alliance.ui.components.team.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.chat.MessengerImagesPreviewHost
import com.lastasylum.alliance.ui.screens.teamnews.resolvedTeamNewsImageUrl
import com.lastasylum.alliance.ui.screens.teamnews.teamNewsAuthedImageRequest

private val galleryShape = RoundedCornerShape(20.dp)
private val tileShape = RoundedCornerShape(14.dp)
private val tileBorder = Color.White.copy(alpha = 0.08f)

@Composable
fun TeamNewsImageGallery(
    imagePaths: List<String>,
    modifier: Modifier = Modifier,
    titleOverlay: String? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
    if (imagePaths.isEmpty()) return
    val context = LocalContext.current
    val requests = remember(imagePaths, context) {
        imagePaths.mapNotNull { teamNewsAuthedImageRequest(context, it) }
    }
    val resolvedUrls = remember(imagePaths) {
        imagePaths.mapNotNull { resolvedTeamNewsImageUrl(it) }
    }
    var previewStart by remember { mutableIntStateOf(-1) }
    val layout = teamNewsGalleryLayout(imagePaths.size)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (layout) {
            TeamNewsGalleryLayout.None -> Unit
            TeamNewsGalleryLayout.SingleHero -> {
                GalleryTile(
                    request = requests.firstOrNull(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(galleryShape),
                    onClick = { previewStart = 0 },
                    titleOverlay = titleOverlay,
                )
            }
            TeamNewsGalleryLayout.TwoColumn -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    requests.forEachIndexed { index, req ->
                        GalleryTile(
                            request = req,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(tileShape),
                            onClick = { previewStart = index },
                        )
                    }
                }
            }
            TeamNewsGalleryLayout.ThreeMasonry -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GalleryTile(
                        request = requests.getOrNull(0),
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .clip(tileShape),
                        onClick = { previewStart = 0 },
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        requests.drop(1).forEachIndexed { index, req ->
                            GalleryTile(
                                request = req,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(tileShape),
                                onClick = { previewStart = index + 1 },
                            )
                        }
                    }
                }
            }
            TeamNewsGalleryLayout.FourGrid,
            TeamNewsGalleryLayout.FourPlusOverlay,
            -> {
                val displayCount = minOf(4, requests.size)
                val extra = imagePaths.size - 4
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        requests.take(2).forEachIndexed { index, req ->
                            GalleryTile(
                                request = req,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(tileShape),
                                onClick = { previewStart = index },
                                overlayText = if (index == 1 && layout == TeamNewsGalleryLayout.FourPlusOverlay && extra > 0) {
                                    "+$extra"
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    if (displayCount > 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            requests.drop(2).take(2).forEachIndexed { index, req ->
                                val absoluteIndex = index + 2
                                GalleryTile(
                                    request = req,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(tileShape),
                                    onClick = { previewStart = absoluteIndex },
                                    overlayText = if (
                                        absoluteIndex == 3 &&
                                        layout == TeamNewsGalleryLayout.FourPlusOverlay &&
                                        extra > 0
                                    ) {
                                        "+$extra"
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (previewStart >= 0 && resolvedUrls.isNotEmpty()) {
        MessengerImagesPreviewHost(
            urls = resolvedUrls,
            startIndex = previewStart.coerceIn(0, resolvedUrls.lastIndex),
            onDismiss = { previewStart = -1 },
        )
    }
}

@Composable
private fun GalleryTile(
    request: ImageRequest?,
    modifier: Modifier,
    onClick: () -> Unit,
    titleOverlay: String? = null,
    overlayText: String? = null,
) {
    Box(
        modifier = modifier
            .border(1.dp, tileBorder, tileShape)
            .clickable(onClick = onClick),
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.team_news_gallery_image_cd),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.06f)),
            )
        }
        titleOverlay?.takeIf { it.isNotBlank() }?.let { title ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                )
            }
        }
        overlayText?.let { text ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
