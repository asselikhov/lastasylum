package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.core.view.setPadding
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import coil.Coil
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatAttachment
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import kotlin.math.abs

/**
 * Лента чата в оверлее: каждое сообщение — «баннер» как в мобильных стратегиях
 * (скруглённая подложка, слева маркер/«аватар», две строки текста, справа ✕).
 * Палитра в духе SquadRelay (тёмно-фиолет + #9B7CFF).
 */
object OverlayChatStripUi {
    private val palette: List<Pair<Int, Int>> = listOf(
        Color.parseColor("#FFD54F") to Color.parseColor("#E8EAEF"),
        Color.parseColor("#D4A5FF") to Color.parseColor("#E8DFFF"),
        Color.parseColor("#82B1FF") to Color.parseColor("#DCE7FF"),
        Color.parseColor("#9B7CFF") to Color.parseColor("#DDD4FF"),
        Color.parseColor("#2DD4BF") to Color.parseColor("#B5FFF0"),
        Color.parseColor("#FFB74D") to Color.parseColor("#FFE0B2"),
    )
    private val selfNameColor = Color.parseColor("#C4B5FD")
    private val selfTextColor = Color.parseColor("#E8DFFF")
    private val cardFill = Color.parseColor("#CC141820")
    private val cardStroke = Color.parseColor("#889B7CFF")
    private val noticeAvatarFill = Color.parseColor("#9B7CFF")

    /** Корень области ленты (без ScrollView — клип по высоте в сервисе). */
    fun styleStripContainer(context: Context, root: View) {
        root.setPadding(
            dp(context, 4f).toInt(),
            dp(context, 2f).toInt(),
            dp(context, 4f).toInt(),
            dp(context, 2f).toInt(),
        )
        root.background = null
    }

    fun createLinesContainer(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(0, 0, 0, 0)
        }
    }

    /** Системное сообщение в том же визуальном стиле (нет комнаты, ошибка истории). */
    fun addNoticeLine(context: Context, container: LinearLayout, message: String) {
        val title = context.getString(R.string.app_name)
        addMessageCard(
            context = context,
            container = container,
            avatarLetter = "S",
            avatarGradient = intArrayOf(
                ColorUtils.blendARGB(noticeAvatarFill, Color.BLACK, 0.25f),
                noticeAvatarFill,
            ),
            senderTelegramUsername = null,
            attachments = emptyList(),
            teamTag = null,
            titleText = title,
            titleColor = Color.parseColor("#F4F0FF"),
            senderRole = null,
            stickerStem = null,
            bodyText = message.trimEnd(),
            bodyColor = Color.parseColor("#D8D0EC"),
            showDismiss = false,
        )
    }

    fun addLine(
        context: Context,
        container: LinearLayout,
        teamTag: String?,
        username: String,
        text: String,
        senderId: String?,
        senderRole: String?,
        senderTelegramUsername: String?,
        attachments: List<ChatAttachment> = emptyList(),
        selfUserId: String?,
        showDismiss: Boolean = true,
    ) {
        val stickerStem = ZlobyakaStickerPack.parseStem(text)
        val rawBody = text.trimEnd()
        val hasImage = firstImageAttachment(attachments) != null
        val preview = when {
            stickerStem != null -> context.getString(R.string.chat_reply_preview_sticker)
            hasImage && rawBody.isBlank() -> ""
            rawBody.length > STRIP_TEXT_MAX_CHARS -> truncateStripText(rawBody, STRIP_TEXT_MAX_CHARS)
            else -> rawBody
        }
        val safeName = username.trim().take(22).ifBlank { "—" }
        val initial = safeName.first().uppercaseChar()
        val (accent, bodyMuted) = colorsFor(senderId, safeName, selfUserId)
        val isSelf = !selfUserId.isNullOrBlank() && senderId == selfUserId
        val titleColor = if (isSelf) selfNameColor else Color.parseColor("#F4F0FF")
        val bodyColor = if (isSelf) selfTextColor else bodyMuted
        val avatarColors = intArrayOf(
            ColorUtils.blendARGB(accent, Color.BLACK, 0.35f),
            accent,
        )
        addMessageCard(
            context = context,
            container = container,
            avatarLetter = initial.toString(),
            avatarGradient = avatarColors,
            senderTelegramUsername = senderTelegramUsername,
            attachments = attachments,
            teamTag = teamTag?.trim()?.takeIf { it.isNotBlank() }?.take(8),
            titleText = safeName,
            titleColor = titleColor,
            senderRole = senderRole?.trim()?.take(12)?.takeIf { it.isNotBlank() },
            stickerStem = stickerStem,
            bodyText = preview,
            bodyColor = bodyColor,
            showDismiss = showDismiss,
        )
    }

    private fun firstImageAttachment(attachments: List<ChatAttachment>): ChatAttachment? =
        attachments.firstOrNull { it.kind == "image" && it.url.isNotBlank() }

    private fun addMessageCard(
        context: Context,
        container: LinearLayout,
        avatarLetter: String,
        avatarGradient: IntArray,
        senderTelegramUsername: String?,
        attachments: List<ChatAttachment>,
        teamTag: String?,
        titleText: String,
        titleColor: Int,
        senderRole: String?,
        stickerStem: String?,
        bodyText: String,
        bodyColor: Int,
        showDismiss: Boolean = true,
    ) {
        val avatarSide = dp(context, 34f).toInt()
        val cornerCard = dp(context, 14f)

        val avatarUrl = telegramAvatarUrl(senderTelegramUsername)
        val avatarImage = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = if (avatarUrl != null) View.VISIBLE else View.GONE
            contentDescription = null
        }
        val avatarInitial = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.CENTER
            text = avatarLetter
            setTextColor(Color.parseColor("#F8F6FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                avatarGradient,
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 8f)
            }
            visibility = View.VISIBLE
        }
        val avatarHost = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(avatarSide, avatarSide).apply {
                marginEnd = dp(context, 6f).toInt()
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            addView(avatarImage)
            addView(avatarInitial)
        }

        if (avatarUrl != null) {
            Coil.imageLoader(context).enqueue(
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .size(128)
                    .target(avatarImage)
                    .listener(
                        object : ImageRequest.Listener {
                            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                                avatarInitial.visibility = View.GONE
                            }

                            override fun onError(request: ImageRequest, result: ErrorResult) {
                                avatarInitial.visibility = View.VISIBLE
                                avatarImage.setImageDrawable(null)
                            }
                        },
                    )
                    .build(),
            )
        }

        val tagView = TextView(context).apply {
            val t = teamTag.orEmpty()
            visibility = if (t.isNotBlank()) View.VISIBLE else View.GONE
            text = "[$t]"
            setTextColor(Color.parseColor("#C4B5FD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(context, 5f).toInt()
            }
        }

        val titleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
            text = titleText
            setTextColor(titleColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val roleTrimmed = senderRole?.trim().orEmpty()
        val roleBadge = TextView(context).apply {
            visibility = if (roleTrimmed.isNotBlank()) View.VISIBLE else View.GONE
            text = roleTrimmed
            setTextColor(roleOnAccentArgb(roleTrimmed))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(context, 56f).toInt()
            setPadding(
                dp(context, 5f).toInt(),
                dp(context, 2f).toInt(),
                dp(context, 5f).toInt(),
                dp(context, 2f).toInt(),
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 6f)
                setColor(ColorUtils.setAlphaComponent(roleAccentArgb(roleTrimmed), 0xE8))
                setStroke(dp(context, 1f).toInt().coerceAtLeast(1), roleAccentArgb(roleTrimmed))
            }
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(tagView)
            addView(titleView)
            addView(roleBadge)
        }

        val stickerSide = dp(context, 56f).toInt()
        val imageMaxH = dp(context, 108f).toInt()
        val firstImage = firstImageAttachment(attachments)
        val bodyView: View = when {
            !stickerStem.isNullOrBlank() -> ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(stickerSide, stickerSide).apply {
                    topMargin = dp(context, 2f).toInt()
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                contentDescription = context.getString(R.string.cd_chat_sticker)
                Coil.imageLoader(context).enqueue(
                    ImageRequest.Builder(context)
                        .data(ZlobyakaStickerPack.assetUriForStem(stickerStem))
                        .size(160)
                        .target(this)
                        .build(),
                )
            }
            firstImage != null -> {
                val url = resolvedChatAttachmentImageUrl(firstImage.url)
                val column = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(context, 2f).toInt() }
                }
                val img = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    maxHeight = imageMaxH
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = context.getString(R.string.chat_attachments_open)
                }
                column.addView(img)
                Coil.imageLoader(context).enqueue(
                    overlayAuthedImageRequest(context, url) {
                        target(img)
                        size(480)
                    },
                )
                if (bodyText.isNotBlank()) {
                    column.addView(
                        TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply { topMargin = dp(context, 3f).toInt() }
                            text = bodyText
                            setTextColor(bodyColor)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                            maxLines = 6
                            ellipsize = TextUtils.TruncateAt.END
                        },
                    )
                }
                column
            }
            else -> TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(context, 2f).toInt() }
                text = bodyText
                setTextColor(bodyColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                maxLines = 10
                ellipsize = TextUtils.TruncateAt.END
            }
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
            addView(titleRow)
            addView(bodyView)
        }

        val closeSize = dp(context, 32f).toInt()
        val close = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(closeSize, closeSize).apply {
                marginStart = dp(context, 2f).toInt()
            }
            visibility = if (showDismiss) View.VISIBLE else View.GONE
            gravity = Gravity.CENTER
            text = "✕"
            contentDescription = context.getString(R.string.overlay_chat_dismiss_cd)
            setTextColor(Color.parseColor("#C4B8DC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(context, 2f).toInt(), 0, dp(context, 2f).toInt(), 0)
        }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(context, 9f).toInt(),
                dp(context, 6f).toInt(),
                dp(context, 8f).toInt(),
                dp(context, 6f).toInt(),
            )
            addView(avatarHost)
            addView(textColumn)
            addView(close)
        }

        val themed = OverlayTickerUi.themedFabContext(context)
        val card = MaterialCardView(themed).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(context, 1f).toInt()
            }
            radius = cornerCard
            cardElevation = dp(context, 1.5f)
            maxCardElevation = dp(context, 4f)
            strokeWidth = dp(context, 1f).toInt().coerceAtLeast(1)
            setStrokeColor(ColorStateList.valueOf(cardStroke))
            setCardBackgroundColor(ColorStateList.valueOf(cardFill))
            setUseCompatPadding(true)
            preventCornerOverlap = false
            isClickable = false
            addView(
                inner,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        if (showDismiss) {
            close.setOnClickListener {
                (card.parent as? LinearLayout)?.removeView(card)
            }
        }

        container.addView(card)
    }

    fun clearLines(container: LinearLayout) {
        container.removeAllViews()
    }

    private fun colorsFor(
        senderId: String?,
        senderName: String,
        selfUserId: String?,
    ): Pair<Int, Int> {
        if (!selfUserId.isNullOrBlank() && senderId == selfUserId) {
            return selfNameColor to selfTextColor
        }
        val key = when {
            !senderId.isNullOrBlank() -> abs(senderId.hashCode())
            else -> abs(senderName.hashCode())
        }
        return palette[key % palette.size]
    }

    private fun dp(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        )
    }

    /** Лимит символов в превью; переносы строк сохраняются до обрезки. */
    private const val STRIP_TEXT_MAX_CHARS = 220

    private fun truncateStripText(s: String, maxChars: Int): String {
        if (s.length <= maxChars) return s
        return s.take(maxChars).trimEnd() + "…"
    }
}
