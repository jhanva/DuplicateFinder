package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.model.OverlayRegion
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class OverlayOnnxMaskRefinerSupportTest {

    @Test
    fun `build mobile sam box prompt uses region corners and box labels`() {
        val prompt = buildMobileSamBoxPrompt(
            region = OverlayRegion(
                left = 0.1f,
                top = 0.2f,
                right = 0.9f,
                bottom = 0.8f,
                confidence = 0.9f,
                kind = OverlayKind.CAPTION
            ),
            width = 1024,
            height = 1024
        )

        assertArrayEquals(floatArrayOf(102.4f, 204.8f, 921.6f, 819.2f), prompt.pointCoords, 0.001f)
        assertArrayEquals(floatArrayOf(2f, 3f), prompt.pointLabels, 0.001f)
    }
}
