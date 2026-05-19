package com.lastasylum.alliance.overlay

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

object OverlayDeviceGallery {
    private const val DEFAULT_LIMIT = 120

    fun requiredReadPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            requiredReadPermission(),
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun loadRecentImageUris(context: Context, limit: Int = DEFAULT_LIMIT): List<Uri> {
        if (!hasReadPermission(context)) return emptyList()
        val appContext = context.applicationContext
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val out = ArrayList<Uri>(limit.coerceAtMost(DEFAULT_LIMIT))
        appContext.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && out.size < limit) {
                val id = cursor.getLong(idCol)
                out.add(
                    Uri.withAppendedPath(collection, id.toString()),
                )
            }
        }
        return out
    }
}
