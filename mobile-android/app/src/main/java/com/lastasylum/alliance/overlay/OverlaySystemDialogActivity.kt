package com.lastasylum.alliance.overlay

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Прозрачный хост для системного пикера/разрешений из оверлея (Service не может быть Activity Result owner).
 *
 * Перед запуском [CombatOverlayService] снимает полноэкранный overlay-чат с [WindowManager]:
 * иначе TYPE_APPLICATION_OVERLAY перекрывает обычную Activity пикера.
 */
class OverlaySystemDialogActivity : ComponentActivity() {
    private var pendingRequestCode: Int = -1
    private var pendingKind: String? = null
    private var launchedPicker: Boolean = false
    private var deliveredResult: Boolean = false

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
    ) { uris ->
        deliverPickImages(uris)
    }

    private val getContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        deliverGetContent(uri)
    }

    private val requestMicLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        deliverMicPermission(granted)
    }

    private val requestGalleryReadLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        deliverGalleryReadPermission(granted)
    }

    private val requestMultipleGalleryReadLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        deliverGalleryReadPermission(OverlayDeviceGallery.hasReadPermission(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOpaquePickerWindow()
        pendingRequestCode = intent?.getIntExtra(EXTRA_REQUEST_CODE, -1) ?: -1
        pendingKind = intent?.getStringExtra(EXTRA_KIND)
        if (savedInstanceState?.getBoolean(STATE_LAUNCHED) == true) {
            deliverCanceled()
            return
        }
        launchPendingKind()
    }

    private fun applyOpaquePickerWindow() {
        val opaque = 0xFF10141E.toInt()
        window.setBackgroundDrawable(ColorDrawable(opaque))
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun launchPendingKind() {
        if (launchedPicker || pendingRequestCode < 0) {
            finish()
            return
        }
        launchedPicker = true
        when (pendingKind) {
            KIND_PICK_IMAGES ->
                pickImagesLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            KIND_GET_CONTENT -> {
                val mime = intent?.getStringExtra(EXTRA_CONTENT_MIME) ?: "image/*"
                getContentLauncher.launch(mime)
            }
            KIND_REQUEST_MIC -> requestMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
            KIND_REQUEST_GALLERY_READ -> launchGalleryReadPermissionRequest()
            else -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LAUNCHED, launchedPicker)
    }

    override fun onDestroy() {
        if (isFinishing && !deliveredResult && !isChangingConfigurations) {
            deliverCanceled()
        }
        super.onDestroy()
    }

    private fun deliverPickImages(uris: List<Uri>) {
        deliveredResult = true
        val requestCode = pendingRequestCode
        val copied = OverlayPickedImages.copyToCache(this, uris)
        val copyFailed = uris.isNotEmpty() && copied.isEmpty()
        val partialCopyFailed = uris.isNotEmpty() && copied.isNotEmpty() && copied.size < uris.size
        sendBroadcast(
            Intent(ACTION_OVERLAY_PICK_IMAGES_RESULT)
                .setPackage(packageName)
                .putExtras(
                    bundleOf(
                        EXTRA_REQUEST_CODE to requestCode,
                        EXTRA_URIS to ArrayList(copied),
                        EXTRA_COPY_FAILED to copyFailed,
                        EXTRA_PARTIAL_COPY_FAILED to partialCopyFailed,
                        EXTRA_PICKED_COUNT to uris.size,
                        EXTRA_COPIED_COUNT to copied.size,
                    ),
                ),
        )
        finish()
    }

    private fun deliverGetContent(uri: Uri?) {
        deliveredResult = true
        val requestCode = pendingRequestCode
        val copied = uri?.let { OverlayPickedImages.copyToCache(this, listOf(it)).firstOrNull() }
        val copyFailed = uri != null && copied == null
        sendBroadcast(
            Intent(ACTION_OVERLAY_GET_CONTENT_RESULT)
                .setPackage(packageName)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
                .putExtra(EXTRA_URI, copied)
                .putExtra(EXTRA_COPY_FAILED, copyFailed),
        )
        finish()
    }

    private fun launchGalleryReadPermissionRequest() {
        val perms = OverlayDeviceGallery.requiredReadPermissions()
        if (perms.size > 1) {
            requestMultipleGalleryReadLauncher.launch(perms)
        } else {
            requestGalleryReadLauncher.launch(perms.first())
        }
    }

    private fun deliverGalleryReadPermission(granted: Boolean) {
        deliveredResult = true
        val requestCode = pendingRequestCode
        sendBroadcast(
            Intent(ACTION_OVERLAY_GALLERY_PERMISSION_RESULT)
                .setPackage(packageName)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
                .putExtra(EXTRA_GRANTED, granted),
        )
        finish()
    }

    private fun deliverMicPermission(granted: Boolean) {
        deliveredResult = true
        val requestCode = pendingRequestCode
        sendBroadcast(
            Intent(ACTION_OVERLAY_MIC_PERMISSION_RESULT)
                .setPackage(packageName)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
                .putExtra(EXTRA_GRANTED, granted),
        )
        finish()
    }

    private fun deliverCanceled() {
        if (deliveredResult) return
        deliveredResult = true
        sendBroadcast(
            Intent(ACTION_OVERLAY_ACTIVITY_CANCELED)
                .setPackage(packageName)
                .putExtra(EXTRA_REQUEST_CODE, pendingRequestCode)
                .putExtra(EXTRA_KIND, pendingKind),
        )
        finish()
    }

    private fun galleryReadPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    companion object {
        private const val STATE_LAUNCHED = "launched_picker"

        const val EXTRA_KIND = "kind"
        const val EXTRA_REQUEST_CODE = "request_code"
        const val KIND_PICK_IMAGES = "pick_images"
        const val KIND_GET_CONTENT = "get_content"
        const val KIND_REQUEST_MIC = "request_mic"
        const val KIND_REQUEST_GALLERY_READ = "request_gallery_read"
        const val EXTRA_PERMISSION = "permission"
        const val EXTRA_KEEP_OVERLAY_VISIBLE = "keep_overlay_visible"

        const val ACTION_OVERLAY_PICK_IMAGES_RESULT = "com.lastasylum.alliance.overlay.PICK_IMAGES_RESULT"
        const val ACTION_OVERLAY_GALLERY_PERMISSION_RESULT =
            "com.lastasylum.alliance.overlay.GALLERY_PERMISSION_RESULT"
        const val ACTION_OVERLAY_GET_CONTENT_RESULT = "com.lastasylum.alliance.overlay.GET_CONTENT_RESULT"
        const val ACTION_OVERLAY_MIC_PERMISSION_RESULT = "com.lastasylum.alliance.overlay.MIC_PERMISSION_RESULT"
        const val ACTION_OVERLAY_ACTIVITY_CANCELED =
            "com.lastasylum.alliance.overlay.ACTIVITY_CANCELED"

        const val EXTRA_URIS = "uris"
        const val EXTRA_URI = "uri"
        const val EXTRA_CONTENT_MIME = "content_mime"
        const val EXTRA_GRANTED = "granted"
        const val EXTRA_COPY_FAILED = "copy_failed"
        const val EXTRA_PARTIAL_COPY_FAILED = "partial_copy_failed"
        const val EXTRA_PICKED_COUNT = "picked_count"
        const val EXTRA_COPIED_COUNT = "copied_count"
    }
}
