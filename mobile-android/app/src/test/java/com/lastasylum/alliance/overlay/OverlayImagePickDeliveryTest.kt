package com.lastasylum.alliance.overlay

import android.app.Activity
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OverlayImagePickDeliveryTest {
    @Test
    fun intentForPickedImages_roundTripsThroughContractParser() {
        val uris = listOf(
            Uri.parse("content://test/one"),
            Uri.parse("content://test/two"),
        )
        val data = OverlayImagePickDelivery.intentForPickedImages(uris)
        val parsed = OverlayImagePickDelivery.parsePickedImages(Activity.RESULT_OK, data)
        assertEquals(uris, parsed)
    }

    @Test
    fun intentForPickedImages_emptyIntentWhenNoUris() {
        assertNull(OverlayImagePickDelivery.intentForPickedImages(emptyList()).clipData)
    }

    @Test
    fun parsePickedImages_emptyWhenCanceled() {
        assertTrue(
            OverlayImagePickDelivery.parsePickedImages(Activity.RESULT_CANCELED, null).isEmpty(),
        )
    }

    @Test
    fun intentForGetContent_setsDataUri() {
        val uri = Uri.parse("content://test/apk")
        val data = OverlayImagePickDelivery.intentForGetContent(uri)
        assertEquals(uri, data.data)
        assertNotNull(data.data)
    }

    @Test
    fun parseGetContent_roundTripsThroughContractParser() {
        val uri = Uri.parse("content://test/photo")
        val data = OverlayImagePickDelivery.intentForGetContent(uri)
        assertEquals(uri, OverlayImagePickDelivery.parseGetContent(Activity.RESULT_OK, data))
    }
}
