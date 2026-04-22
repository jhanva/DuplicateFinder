package com.duplicatefinder.domain.model

import com.duplicatefinder.domain.testImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayCleaningSupportTest {

    @Test
    fun `overlay cleaning support accepts common still-image formats and rejects unsupported ones`() {
        val jpeg = testImage(id = 1, size = 100).copy(mimeType = "image/jpeg")
        val png = testImage(id = 2, size = 100).copy(mimeType = "image/png")
        val webp = testImage(id = 3, size = 100).copy(mimeType = "image/webp")
        val gif = testImage(id = 4, size = 100, name = "image_4.gif").copy(mimeType = "image/gif")
        val heic = testImage(id = 5, size = 100, name = "image_5.heic").copy(mimeType = "image/heic")

        assertTrue(jpeg.supportsOverlayCleaning())
        assertTrue(png.supportsOverlayCleaning())
        assertTrue(webp.supportsOverlayCleaning())
        assertFalse(gif.supportsOverlayCleaning())
        assertFalse(heic.supportsOverlayCleaning())
    }

    @Test
    fun `preview generation keeps source dimensions when metadata is available`() {
        val image = testImage(id = 1, size = 100).copy(width = 4032, height = 3024)

        assertEquals(4032, image.overlayPreviewDecodeMaxDimension(minDimension = 1024))
    }
}
