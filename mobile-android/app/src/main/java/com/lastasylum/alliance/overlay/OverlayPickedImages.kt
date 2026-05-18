package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Копирует URI из системного пикера в cache + FileProvider: после [OverlaySystemDialogActivity.finish]
 * временные grant'ы на content:// часто снимаются, и загрузка из Service/VM падает.
 */
object OverlayPickedImages {
    private const val TAG = "OverlayPickedImages"

    fun copyToCache(context: Context, uris: List<Uri>): List<Uri> {
        if (uris.isEmpty()) return emptyList()
        val dir = File(context.cacheDir, "overlay_chat_picks").apply { mkdirs() }
        return uris.mapNotNull { source -> copyOne(context, source, dir) }
    }

    private fun copyOne(context: Context, source: Uri, dir: File): Uri? {
        val ext = guessExtension(context, source)
        val outFile = File(dir, "pick_${System.currentTimeMillis()}_${UUID.randomUUID()}.$ext")
        return runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    source,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val input = context.contentResolver.openInputStream(source)
                ?: return@runCatching null
            input.use { inp ->
                outFile.outputStream().use { out -> inp.copyTo(out) }
            }
            if (outFile.length() <= 0L) {
                outFile.delete()
                return@runCatching null
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile,
            )
        }.onFailure { e ->
            Log.w(TAG, "copyOne failed for $source", e)
        }.getOrNull()
    }

    private fun guessExtension(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        if (mime.isNotBlank()) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        }.getOrNull().orEmpty()
        val fromName = name.substringAfterLast('.', "").lowercase()
        if (fromName.length in 1..5) return fromName
        return "jpg"
    }
}
