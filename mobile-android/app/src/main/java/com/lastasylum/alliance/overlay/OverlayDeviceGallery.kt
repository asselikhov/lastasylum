package com.lastasylum.alliance.overlay

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

object OverlayDeviceGallery {
    private const val DEFAULT_LIMIT = 180

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
        val collections = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
                add(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            }
        }.distinctBy { it.toString() }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val seen = LinkedHashSet<String>(limit)
        val out = ArrayList<Uri>(limit)

        for (collection in collections) {
            if (out.size >= limit) break
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
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    if (seen.add(uri.toString())) {
                        out.add(uri)
                    }
                }
            }
        }
        return out
    }
}
