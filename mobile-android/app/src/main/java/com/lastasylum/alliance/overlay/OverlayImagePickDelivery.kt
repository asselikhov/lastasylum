package com.lastasylum.alliance.overlay

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Сборка [Intent] для [ActivityResultRegistry.dispatchResult] после системного пикера оверлея.
 */
object OverlayImagePickDelivery {
    private val pickMultipleContract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12)

    fun parsePickedImages(resultCode: Int, data: Intent?): List<Uri> {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return emptyList()
        return pickMultipleContract.parseResult(resultCode, data)
    }

    fun intentForPickedImages(uris: List<Uri>): Intent {
        if (uris.isEmpty()) return Intent()
        val clip = ClipData.newRawUri("images", uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return Intent().apply { clipData = clip }
    }

    fun intentForGetContent(uri: Uri?): Intent =
        if (uri != null) Intent().setData(uri) else Intent()

    fun parseGetContent(resultCode: Int, data: Intent?): Uri? {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return null
        return ActivityResultContracts.GetContent().parseResult(resultCode, data)
    }
}
