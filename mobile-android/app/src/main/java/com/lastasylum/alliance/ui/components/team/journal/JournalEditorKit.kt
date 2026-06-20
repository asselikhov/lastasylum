package com.lastasylum.alliance.ui.components.team.journal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lastasylum.alliance.ui.components.team.PremiumJournalFeedTokens
import com.lastasylum.alliance.ui.theme.premium.PremiumColors

@Immutable
object JournalEditorTokens {
    val fieldShape = RoundedCornerShape(16.dp)
    val chipShape = RoundedCornerShape(16.dp)
    val buttonShape = RoundedCornerShape(16.dp)
    val thumbShape = RoundedCornerShape(14.dp)
    val fieldPaddingH = 16.dp
    val fieldPaddingV = 14.dp
    val sectionGap = 16.dp
    val screenHorizontalPad = 16.dp
    val thumbSize = 88.dp
    val fieldMinHeightMulti = 160.dp

    val fieldBackground = Brush.verticalGradient(
        listOf(
            PremiumJournalFeedTokens.glassTop.copy(alpha = 0.72f),
            PremiumJournalFeedTokens.glassBottom.copy(alpha = 0.82f),
        ),
    )
    val fieldBorderDefault = Color.White.copy(alpha = 0.10f)
    val fieldBorderError = PremiumColors.accentCyan.copy(alpha = 0.65f)
    val labelColor = PremiumJournalFeedTokens.metaColor
    val textColor = PremiumJournalFeedTokens.titleColor
    val hintColor = PremiumJournalFeedTokens.excerptColor

    val fieldTextStyle = TextStyle(
        fontFamily = PremiumJournalFeedTokens.InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = textColor,
    )
    val labelStyle = TextStyle(
        fontFamily = PremiumJournalFeedTokens.InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = labelColor,
    )
}

enum class JournalEditorMode {
    News,
    PollOnly,
}

@Composable
fun JournalSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = JournalEditorTokens.labelStyle.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        ),
        modifier = modifier.padding(bottom = 4.dp),
    )
}

@Composable
fun JournalSectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 4.dp),
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.08f),
    )
}

@Composable
fun JournalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    errorMessage: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    onFocused: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val shape = JournalEditorTokens.fieldShape
    val borderColor = if (isError) JournalEditorTokens.fieldBorderError else JournalEditorTokens.fieldBorderDefault
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = JournalEditorTokens.labelStyle)
            hint?.let {
                Text(
                    text = it,
                    style = JournalEditorTokens.labelStyle.copy(
                        fontWeight = FontWeight.Normal,
                        color = JournalEditorTokens.hintColor,
                    ),
                )
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = JournalEditorTokens.fieldTextStyle,
            cursorBrush = SolidColor(PremiumColors.accentCyan),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (singleLine) {
                        Modifier
                    } else {
                        Modifier
                            .heightIn(min = JournalEditorTokens.fieldMinHeightMulti)
                    },
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (bringIntoViewRequester != null) {
                        Modifier.bringIntoViewRequester(bringIntoViewRequester)
                    } else {
                        Modifier
                    },
                )
                .onFocusChanged { state ->
                    if (state.isFocused) onFocused()
                }
                .clip(shape)
                .background(JournalEditorTokens.fieldBackground)
                .border(1.dp, borderColor, shape)
                .padding(
                    horizontal = JournalEditorTokens.fieldPaddingH,
                    vertical = JournalEditorTokens.fieldPaddingV,
                ),
        )
        if (isError && !errorMessage.isNullOrBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = JournalEditorTokens.fieldBorderError,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = errorMessage,
                    style = JournalEditorTokens.labelStyle.copy(
                        color = JournalEditorTokens.fieldBorderError,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
}

@Composable
fun JournalModeChips(
    selected: JournalEditorMode,
    onSelect: (JournalEditorMode) -> Unit,
    newsLabel: String,
    pollLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        JournalEditorMode.entries.forEach { mode ->
            val isSelected = selected == mode
            val label = when (mode) {
                JournalEditorMode.News -> newsLabel
                JournalEditorMode.PollOnly -> pollLabel
            }
            val icon = when (mode) {
                JournalEditorMode.News -> Icons.AutoMirrored.Outlined.Article
                JournalEditorMode.PollOnly -> Icons.Outlined.Poll
            }
            JournalModeChip(
                label = label,
                icon = icon,
                selected = isSelected,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun JournalModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = JournalEditorTokens.chipShape
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.30f),
                                Color(0xFF818CF8).copy(alpha = 0.26f),
                            ),
                        ),
                    )
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                PremiumJournalFeedTokens.glassTop.copy(alpha = 0.55f),
                                PremiumJournalFeedTokens.glassBottom.copy(alpha = 0.65f),
                            ),
                        ),
                    )
                },
            )
            .border(
                1.dp,
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.55f),
                            Color(0xFF818CF8).copy(alpha = 0.45f),
                        ),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)),
                    )
                },
                shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else PremiumJournalFeedTokens.metaColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else PremiumJournalFeedTokens.titleColor,
                maxLines = 1,
            )
        }
    }
}

data class JournalImageAttachment(
    val fileId: String?,
    val previewRequest: ImageRequest?,
    val uploading: Boolean = false,
    val uploadFailed: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalImageAttachmentGrid(
    attachments: List<JournalImageAttachment>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    addLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxImages: Int = 10,
    onPreview: ((Int) -> Unit)? = null,
) {
    val readyCount = attachments.count { !it.uploading && !it.fileId.isNullOrBlank() }
    val canAdd = enabled && readyCount < maxImages && !attachments.any { it.uploading }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            attachments.size == 1 && !attachments[0].uploading -> {
                EditorImageTile(
                    attachment = attachments[0],
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    onRemove = { onRemove(0) },
                    onPreview = onPreview?.let { { it(0) } },
                    enabled = enabled,
                )
            }
            attachments.isEmpty() -> Unit
            else -> {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    attachments.forEachIndexed { index, attachment ->
                        EditorImageTile(
                            attachment = attachment,
                            modifier = Modifier.size(
                                if (attachments.size >= 5) 100.dp else JournalEditorTokens.thumbSize,
                            ),
                            onRemove = { onRemove(index) },
                            onPreview = onPreview?.let { { it(index) } },
                            enabled = enabled,
                        )
                    }
                }
            }
        }
        if (canAdd) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(JournalEditorTokens.thumbShape)
                    .background(JournalEditorTokens.fieldBackground)
                    .border(1.dp, JournalEditorTokens.fieldBorderDefault, JournalEditorTokens.thumbShape)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = addLabel,
                        tint = PremiumColors.accentCyan,
                    )
                    Text(
                        text = addLabel,
                        style = JournalEditorTokens.labelStyle,
                        color = JournalEditorTokens.hintColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorImageTile(
    attachment: JournalImageAttachment,
    modifier: Modifier,
    onRemove: () -> Unit,
    onPreview: (() -> Unit)?,
    enabled: Boolean,
) {
    Box(
        modifier = modifier
            .clip(JournalEditorTokens.thumbShape)
            .background(JournalEditorTokens.fieldBackground)
            .border(
                1.dp,
                if (attachment.uploadFailed) {
                    JournalEditorTokens.fieldBorderError
                } else {
                    JournalEditorTokens.fieldBorderDefault
                },
                JournalEditorTokens.thumbShape,
            )
            .then(
                if (onPreview != null && !attachment.uploading && attachment.previewRequest != null) {
                    Modifier.clickable(onClick = onPreview)
                } else {
                    Modifier
                },
            ),
    ) {
        attachment.previewRequest?.let { req ->
            AsyncImage(
                model = req,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (attachment.uploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            }
        }
        if (enabled && !attachment.uploading) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .padding(2.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                        .padding(2.dp),
                )
            }
        }
    }
}

@Composable
fun JournalPollOptionEditor(
    options: List<String>,
    optionLabels: List<String>,
    onOptionChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minOptions: Int = 2,
    enabled: Boolean = true,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    onOptionFocused: (Int) -> Unit = {},
    focusRequesters: List<FocusRequester>? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(JournalEditorTokens.sectionGap),
    ) {
        options.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val isLast = index == options.lastIndex
                JournalTextField(
                    value = value,
                    onValueChange = { onOptionChange(index, it) },
                    label = optionLabels.getOrElse(index) { "Option ${index + 1}" },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    focusRequester = focusRequesters?.getOrNull(index),
                    bringIntoViewRequester = bringIntoViewRequester,
                    onFocused = { onOptionFocused(index) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusRequesters?.getOrNull(index + 1)?.requestFocus()
                        },
                    ),
                )
                if (options.size > minOptions) {
                    IconButton(
                        onClick = { onRemove(index) },
                        enabled = enabled,
                        modifier = Modifier.padding(top = 22.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = JournalEditorTokens.hintColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JournalPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val shape = JournalEditorTokens.buttonShape
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.32f),
                            Color(0xFF818CF8).copy(alpha = 0.28f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF2A3444).copy(alpha = 0.6f),
                            Color(0xFF2A3444).copy(alpha = 0.5f),
                        ),
                    )
                },
            )
            .border(
                1.dp,
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.58f),
                            Color(0xFF818CF8).copy(alpha = 0.48f),
                        ),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f)),
                    )
                },
                shape,
            )
            .then(
                if (enabled && !loading) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else JournalEditorTokens.hintColor,
            )
        }
    }
}

@Composable
fun JournalEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Outlined.Article,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = JournalEditorTokens.hintColor.copy(alpha = 0.7f),
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = JournalEditorTokens.hintColor,
        )
    }
}

@Composable
fun JournalListSkeleton(
    itemCount: Int = 4,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "journal_list_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    val base = Color(0xFF1E2836).copy(alpha = alpha)
    val shape = PremiumJournalFeedTokens.cardShape
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(PremiumJournalFeedTokens.listSpacing),
    ) {
        repeat(itemCount) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                PremiumJournalFeedTokens.glassTop.copy(alpha = 0.5f),
                                PremiumJournalFeedTokens.glassBottom.copy(alpha = 0.6f),
                            ),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(base),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(base.copy(alpha = alpha * 0.85f)),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(base.copy(alpha = alpha * 0.7f)),
                )
            }
        }
    }
}
