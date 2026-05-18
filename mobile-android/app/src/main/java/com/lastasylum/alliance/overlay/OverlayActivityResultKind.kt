package com.lastasylum.alliance.overlay

import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Maps [ActivityResultContract] instances from overlay Compose launchers to
 * [OverlaySystemDialogActivity] dialog kinds.
 */
object OverlayActivityResultKind {
    fun kindFor(contract: ActivityResultContract<*, *>): String? = when (contract) {
        is ActivityResultContracts.PickMultipleVisualMedia,
        is ActivityResultContracts.PickVisualMedia,
        -> OverlaySystemDialogActivity.KIND_PICK_IMAGES
        is ActivityResultContracts.GetContent -> OverlaySystemDialogActivity.KIND_GET_CONTENT
        is ActivityResultContracts.RequestPermission -> OverlaySystemDialogActivity.KIND_REQUEST_MIC
        else -> null
    }
}
