package com.lastasylum.alliance.overlay

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

object OverlayDeviceGallery {
    private const val DEFAULT_LIMIT = 180

    fun requiredReadPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 34) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun requiredReadPermission(): String = requiredReadPermissions().first()

    fun hasReadPermission(context: Context): Boolean =
        requiredReadPermissions().any { perm ->
            ContextCompat.checkSelfPermission(context, perm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    /** Полный доступ к галерее (не только «выбранные фото» на Android 14+). */
    fun hasFullGalleryAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            hasReadPermission(context)
        }

    /** Только частичный доступ Photo Picker — MediaStore показывает лишь ранее выданные снимки. */
    fun hasPartialGalleryAccessOnly(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 34 &&
            !hasFullGalleryAccess(context) &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun loadRecentImageUris(context: Context, limit: Int = DEFAULT_LIMIT): List<Uri> {
        if (!hasReadPermission(context)) return emptyList()
        val appContext = context.applicationContext
        val collections = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
                add(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
            }
            add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
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
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                if (idCol < 0) return@use
                while (cursor.moveToNext() && out.size < limit) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    if (seen.add(uri.toString())) {
                        out.add(uri)
                    }
                }
            }
        }
        return out
    }
}
