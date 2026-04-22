package com.duplicatefinder.presentation.screens.overlay

import android.content.Intent
import com.duplicatefinder.domain.testImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungGalleryEditIntentFactoryTest {

    @Test
    fun `availability is enabled only for samsung gallery on samsung devices`() {
        val factory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "samsung",
            canResolveEditIntent = { true }
        )

        val availability = factory.availabilityFor(testImage(id = 1, size = 100))

        assertTrue(availability.enabled)
        assertEquals(null, availability.reason)
    }

    @Test
    fun `availability is disabled on non samsung devices`() {
        val factory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "google",
            canResolveEditIntent = { true }
        )

        val availability = factory.availabilityFor(testImage(id = 1, size = 100))

        assertFalse(availability.enabled)
        assertEquals(
            "Requires Samsung Gallery AI editing on a supported Samsung device.",
            availability.reason
        )
    }

    @Test
    fun `create intent targets samsung gallery with edit permissions`() {
        val image = testImage(id = 1, size = 100)
        val factory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "samsung",
            canResolveEditIntent = { true }
        )

        val intent = factory.createIntent(image)

        assertEquals(Intent.ACTION_EDIT, intent.action)
        assertEquals(image.uri, intent.data)
        assertEquals(image.mimeType, intent.type)
        assertEquals("com.sec.android.gallery3d", intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    }
}
