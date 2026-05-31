package com.lastasylum.alliance.overlay

internal object OverlayReactionExpiryPolicy {
    private const val TEXT_HERO_MULTIPLIER = 1.35f
    private const val STICKER_GIF_MINI_MULTIPLIER = 0.85f

    fun heroExpiryMs(
        reactionId: String,
        extended: Boolean,
        burstMode: Boolean,
    ): Long {
        val base = OverlayReactionStageLayout.heroExpiryMs(extended)
        if (decodeTextReactionId(reactionId) != null) {
            return (base * TEXT_HERO_MULTIPLIER).toLong()
        }
        if (burstMode) {
            return (base * 0.92f).toLong()
        }
        return base
    }

    fun miniExpiryMs(
        reactionId: String,
        burstMode: Boolean,
        slotIndex: Int,
    ): Long {
        val base = OverlayReactionStageLayout.miniExpiryMs(burstMode)
        val typed = when {
            decodeTextReactionId(reactionId) != null -> base
            isStickerOrGifReactionId(reactionId) -> (base * STICKER_GIF_MINI_MULTIPLIER).toLong()
            else -> base
        }
        return typed + slotIndex * OverlayReactionStageLayout.STAGGER_MS
    }
}
