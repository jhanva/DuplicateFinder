package com.duplicatefinder.presentation.screens.overlay

import com.duplicatefinder.domain.testImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungGalleryEditIntentFactoryTest {
    private val requiredMessage = "Requires Samsung Gallery AI editing on a supported Samsung device."
    private val advisoryMessage =
        "Opens the image in Samsung Gallery. Tap Edit in Gallery to use AI tools like Object Eraser."

    @Test
    fun `availability includes advisory note on supported samsung devices`() {
        val factory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "samsung",
            requiredMessage = requiredMessage,
            advisoryMessage = advisoryMessage,
            isSamsungGalleryInstalled = { true }
        )

        val availability = factory.availabilityFor(testImage(id = 1, size = 100))

        assertTrue(availability.enabled)
        assertEquals(advisoryMessage, availability.helperText)
    }

    @Test
    fun `availability is disabled on non samsung devices`() {
        val factory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "google",
            requiredMessage = requiredMessage,
            advisoryMessage = advisoryMessage,
            isSamsungGalleryInstalled = { true }
        )

        val availability = factory.availabilityFor(testImage(id = 1, size = 100))

        assertFalse(availability.enabled)
        assertEquals(requiredMessage, availability.helperText)
    }

    @Test
    fun `availability stays enabled when samsung gallery is installed`() {
        val factory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "samsung",
            requiredMessage = requiredMessage,
            advisoryMessage = advisoryMessage,
            isSamsungGalleryInstalled = { true }
        )

        val availability = factory.availabilityFor(testImage(id = 1, size = 100))

        assertTrue(availability.enabled)
        assertEquals(advisoryMessage, availability.helperText)
    }

    @Test
    fun `availability is disabled when samsung gallery package is not installed`() {
        val factory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "samsung",
            requiredMessage = requiredMessage,
            advisoryMessage = advisoryMessage,
            isSamsungGalleryInstalled = { false }
        )

        val availability = factory.availabilityFor(testImage(id = 1, size = 100))

        assertFalse(availability.enabled)
        assertEquals(requiredMessage, availability.helperText)
    }

    @Test
    fun `create launch specs prefer samsung gallery viewer first and preserve image mime type`() {
        val image = testImage(id = 1, size = 100)
        val factory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "samsung",
            requiredMessage = requiredMessage,
            advisoryMessage = advisoryMessage,
            isSamsungGalleryInstalled = { true }
        )

        val specs = factory.createLaunchSpecs(image)

        assertEquals("android.intent.action.VIEW", specs.first().action)
        assertEquals(image.mimeType, specs.first().mimeType)
        assertEquals("com.samsung.android.gallery.app.activity.GalleryActivity", specs.first().className)
    }

    @Test
    fun `create launch specs includes samsung gallery viewer fallbacks`() {
        val image = testImage(id = 1, size = 100)
        val factory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "samsung",
            requiredMessage = requiredMessage,
            advisoryMessage = advisoryMessage,
            isSamsungGalleryInstalled = { true }
        )

        val specs = factory.createLaunchSpecs(image)

        assertEquals(8, specs.size)
        assertEquals(
            listOf(
                "android.intent.action.VIEW",
                "android.intent.action.VIEW",
                "android.intent.action.VIEW",
                "android.intent.action.VIEW",
                "android.intent.action.VIEW",
                "android.intent.action.VIEW",
                "android.intent.action.VIEW",
                "android.intent.action.VIEW"
            ),
            specs.map { it.action }
        )
        assertEquals(
            listOf(
                image.mimeType,
                "image/*",
                image.mimeType,
                "image/*",
                image.mimeType,
                "image/*",
                image.mimeType,
                "image/*"
            ),
            specs.map { it.mimeType }
        )
        assertEquals(
            listOf(
                "com.samsung.android.gallery.app.activity.GalleryActivity",
                "com.samsung.android.gallery.app.activity.GalleryActivity",
                "com.sec.android.gallery3d.app.GalleryOpaqueActivity",
                "com.sec.android.gallery3d.app.GalleryOpaqueActivity",
                "com.sec.android.gallery3d.app.Gallery",
                "com.sec.android.gallery3d.app.Gallery",
                null,
                null
            ),
            specs.map { it.className }
        )
    }
}
