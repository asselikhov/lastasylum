package com.lastasylum.alliance.overlay

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf

/**
 * Transparent "host" activity to show system dialogs (photo picker / permissions) for overlay UI.
 * Overlay runs from a Service, so ActivityResult-based APIs must be hosted by an Activity.
 */
class OverlaySystemDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.getStringExtra(EXTRA_KIND)) {
            KIND_PICK_IMAGES -> pickImages()
            KIND_REQUEST_MIC -> requestMic()
            else -> finish()
        }
    }

    private fun pickImages() {
        val requestCode = intent?.getIntExtra(EXTRA_REQUEST_CODE, -1) ?: -1
        val launcher = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
        ) { uris ->
            sendBroadcast(
                Intent(ACTION_OVERLAY_PICK_IMAGES_RESULT)
                    .setPackage(packageName)
                    .putExtras(
                        bundleOf(
                            EXTRA_REQUEST_CODE to requestCode,
                            EXTRA_URIS to ArrayList<Uri>(uris),
                        ),
                    ),
            )
            finish()
        }
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun requestMic() {
        val requestCode = intent?.getIntExtra(EXTRA_REQUEST_CODE, -1) ?: -1
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            sendBroadcast(
                Intent(ACTION_OVERLAY_MIC_PERMISSION_RESULT)
                    .setPackage(packageName)
                    .putExtra(EXTRA_REQUEST_CODE, requestCode)
                    .putExtra(EXTRA_GRANTED, granted),
            )
            finish()
        }
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_REQUEST_CODE = "request_code"
        const val KIND_PICK_IMAGES = "pick_images"
        const val KIND_REQUEST_MIC = "request_mic"

        const val ACTION_OVERLAY_PICK_IMAGES_RESULT = "com.lastasylum.alliance.overlay.PICK_IMAGES_RESULT"
        const val ACTION_OVERLAY_MIC_PERMISSION_RESULT = "com.lastasylum.alliance.overlay.MIC_PERMISSION_RESULT"

        const val EXTRA_URIS = "uris"
        const val EXTRA_GRANTED = "granted"
    }
}

