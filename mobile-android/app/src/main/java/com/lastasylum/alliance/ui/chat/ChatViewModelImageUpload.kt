package com.lastasylum.alliance.ui.chat

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatReaction
import java.io.InputStream
import java.util.Locale

internal fun applyOptimisticReactionToggle(
    messages: List<ChatMessage>,
    messageId: String,
    emoji: String,
): List<ChatMessage> {
    val index = messages.indexOfFirst { it._id == messageId }
    if (index < 0) return messages
    val message = messages[index]
    val reactions = message.reactions.toMutableList()
    val at = reactions.indexOfFirst { it.emoji == emoji }
    if (at >= 0) {
        val row = reactions[at]
        if (row.reactedByMe) {
            val nextCount = row.count - 1
            if (nextCount <= 0) {
                reactions.removeAt(at)
            } else {
                reactions[at] = row.copy(count = nextCount, reactedByMe = false)
            }
        } else {
            reactions[at] = row.copy(count = row.count + 1, reactedByMe = true)
        }
    } else {
        reactions.add(
            ChatReaction(
                emoji = emoji,
                count = 1,
                reactedByMe = true,
            ),
        )
    }
    if (reactions == message.reactions) return messages
    val updated = messages.toMutableList()
    updated[index] = message.copy(reactions = reactions)
    return updated
}

internal fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

internal fun sniffImageMimeFromHeader(header: ByteArray): String? {
    if (header.size < 12) return null
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    if (header.hasPrefix(jpeg)) return "image/jpeg"
    val png = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    if (header.hasPrefix(png)) return "image/png"
    if (header.size >= 6) {
        val gif = String(header, 0, 6, Charsets.US_ASCII)
        if (gif == "GIF87a" || gif == "GIF89a") return "image/gif"
    }
    val riff = String(header, 0, 4, Charsets.US_ASCII)
    val webp = String(header, 8, 4, Charsets.US_ASCII)
    if (riff == "RIFF" && webp == "WEBP") return "image/webp"
    if (header.size >= 2 && header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()) return "image/bmp"
    if (header.size >= 12 &&
        header[4] == 'f'.code.toByte() &&
        header[5] == 't'.code.toByte() &&
        header[6] == 'y'.code.toByte() &&
        header[7] == 'p'.code.toByte()
    ) {
        val brand = String(header, 8, 4, Charsets.US_ASCII)
        if (brand.equals("heic", ignoreCase = true) ||
            brand.equals("heix", ignoreCase = true) ||
            brand.equals("mif1", ignoreCase = true) ||
            brand.equals("msf1", ignoreCase = true)
        ) {
            return "image/heic"
        }
    }
    return null
}

internal fun resolveUploadImageMime(declared: String, sniffed: String?): String? {
    val d = declared.trim()
    val dl = d.lowercase(Locale.ROOT)
    return when {
        dl.startsWith("image/") && dl != "image/*" -> d
        dl == "image/*" -> sniffed ?: "image/jpeg"
        sniffed != null -> sniffed
        else -> null
    }
}
