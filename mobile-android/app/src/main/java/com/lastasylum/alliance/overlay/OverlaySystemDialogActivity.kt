package com.lastasylum.alliance.overlay

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRequestCode = intent?.getIntExtra(EXTRA_REQUEST_CODE, -1) ?: -1
        pendingKind = intent?.getStringExtra(EXTRA_KIND)
        if (savedInstanceState?.getBoolean(STATE_LAUNCHED) == true) {
            return
        }
        launchPendingKind()
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
            else -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LAUNCHED, launchedPicker)
    }

    override fun onDestroy() {
        if (isFinishing && !deliveredResult) {
            notifyOverlaySystemUiFinished()
        }
        super.onDestroy()
    }

    private fun notifyOverlaySystemUiFinished() {
        sendBroadcast(
            Intent(ACTION_OVERLAY_SYSTEM_UI_FINISHED)
                .setPackage(packageName),
        )
    }

    private fun deliverPickImages(uris: List<Uri>) {
        deliveredResult = true
        val requestCode = pendingRequestCode
        val copied = OverlayPickedImages.copyToCache(this, uris)
        sendBroadcast(
            Intent(ACTION_OVERLAY_PICK_IMAGES_RESULT)
                .setPackage(packageName)
                .putExtras(
                    bundleOf(
                        EXTRA_REQUEST_CODE to requestCode,
                        EXTRA_URIS to ArrayList(copied),
                    ),
                ),
        )
        finish()
    }

    private fun deliverGetContent(uri: Uri?) {
        deliveredResult = true
        val requestCode = pendingRequestCode
        val copied = uri?.let { OverlayPickedImages.copyToCache(this, listOf(it)).firstOrNull() }
        sendBroadcast(
            Intent(ACTION_OVERLAY_GET_CONTENT_RESULT)
                .setPackage(packageName)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
                .putExtra(EXTRA_URI, copied),
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

    companion object {
        private const val STATE_LAUNCHED = "launched_picker"

        const val EXTRA_KIND = "kind"
        const val EXTRA_REQUEST_CODE = "request_code"
        const val KIND_PICK_IMAGES = "pick_images"
        const val KIND_GET_CONTENT = "get_content"
        const val KIND_REQUEST_MIC = "request_mic"

        const val ACTION_OVERLAY_PICK_IMAGES_RESULT = "com.lastasylum.alliance.overlay.PICK_IMAGES_RESULT"
        const val ACTION_OVERLAY_GET_CONTENT_RESULT = "com.lastasylum.alliance.overlay.GET_CONTENT_RESULT"
        const val ACTION_OVERLAY_MIC_PERMISSION_RESULT = "com.lastasylum.alliance.overlay.MIC_PERMISSION_RESULT"
        const val ACTION_OVERLAY_SYSTEM_UI_FINISHED =
            "com.lastasylum.alliance.overlay.SYSTEM_UI_FINISHED"

        const val EXTRA_URIS = "uris"
        const val EXTRA_URI = "uri"
        const val EXTRA_CONTENT_MIME = "content_mime"
        const val EXTRA_GRANTED = "granted"
    }
}
