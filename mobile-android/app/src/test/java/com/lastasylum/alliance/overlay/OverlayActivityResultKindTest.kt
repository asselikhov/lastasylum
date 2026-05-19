package com.lastasylum.alliance.overlay

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayActivityResultKindTest {
    @Test
    fun kindFor_pickMultiple_mapsToPickImages() {
        assertEquals(
            OverlaySystemDialogActivity.KIND_PICK_IMAGES,
            OverlayActivityResultKind.kindFor(
                ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12),
            ),
        )
    }

    @Test
    fun kindFor_pickVisual_mapsToPickImages() {
        assertEquals(
            OverlaySystemDialogActivity.KIND_PICK_IMAGES,
            OverlayActivityResultKind.kindFor(ActivityResultContracts.PickVisualMedia()),
        )
    }

    @Test
    fun kindFor_getContent_mapsToGetContent() {
        assertEquals(
            OverlaySystemDialogActivity.KIND_GET_CONTENT,
            OverlayActivityResultKind.kindFor(ActivityResultContracts.GetContent()),
        )
    }

    @Test
    fun kindFor_unknown_returnsNull() {
        assertNull(OverlayActivityResultKind.kindFor(ActivityResultContracts.TakePicture()))
    }

    @Test
    fun kindFor_galleryReadPermission_mapsToGalleryRead() {
        assertEquals(
            OverlaySystemDialogActivity.KIND_REQUEST_GALLERY_READ,
            OverlayActivityResultKind.kindFor(
                ActivityResultContracts.RequestPermission(),
                Manifest.permission.READ_MEDIA_IMAGES,
            ),
        )
    }

    @Test
    fun kindFor_micPermission_mapsToMic() {
        assertEquals(
            OverlaySystemDialogActivity.KIND_REQUEST_MIC,
            OverlayActivityResultKind.kindFor(
                ActivityResultContracts.RequestPermission(),
                Manifest.permission.RECORD_AUDIO,
            ),
        )
    }
}
