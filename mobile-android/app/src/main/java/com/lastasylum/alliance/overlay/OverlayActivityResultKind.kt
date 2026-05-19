package com.lastasylum.alliance.overlay

import android.Manifest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Maps [ActivityResultContract] instances from overlay Compose launchers to
 * [OverlaySystemDialogActivity] dialog kinds.
 */
object OverlayActivityResultKind {
    fun kindFor(
        contract: ActivityResultContract<*, *>,
        input: Any? = null,
    ): String? = when (contract) {
        is ActivityResultContracts.PickMultipleVisualMedia,
        is ActivityResultContracts.PickVisualMedia,
        -> OverlaySystemDialogActivity.KIND_PICK_IMAGES
        is ActivityResultContracts.GetContent -> OverlaySystemDialogActivity.KIND_GET_CONTENT
        is ActivityResultContracts.RequestPermission ->
            kindForPermission(input as? String)
        else -> null
    }

    private fun kindForPermission(permission: String?): String? = when (permission) {
        Manifest.permission.RECORD_AUDIO -> OverlaySystemDialogActivity.KIND_REQUEST_MIC
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        -> OverlaySystemDialogActivity.KIND_REQUEST_GALLERY_READ
        else -> null
    }
}
