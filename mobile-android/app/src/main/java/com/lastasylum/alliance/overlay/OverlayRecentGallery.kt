package com.lastasylum.alliance.overlay

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Recent images from MediaStore for in-overlay gallery (no system picker Activity). */
object OverlayRecentGallery {
    fun loadRecentImageUris(context: Context, limit: Int = 96): List<Uri> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val uris = ArrayList<Uri>(limit.coerceAtMost(120))
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && uris.size < limit) {
                val id = cursor.getLong(idCol)
                uris.add(
                    Uri.withAppendedPath(collection, id.toString()),
                )
            }
        }
        return uris
    }
}
