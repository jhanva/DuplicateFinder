package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.repository.OverlayDetectorOutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayOnnxPostProcessorTest {

    @Test
    fun `decode detector output supports normalized boxes`() {
        val regions = OverlayOnnxPostProcessor.decodeDetectorOutput(
            values = floatArrayOf(
                0.1f, 0.7f, 0.9f, 0.92f, 0.87f, OverlayKind.CAPTION.ordinal.toFloat(),
                0.0f, 0.0f, 0.0f, 0.0f, 0.20f, OverlayKind.TEXT.ordinal.toFloat()
            ),
            shape = longArrayOf(1, 2, 6),
            width = 1200,
            height = 800,
            outputFormat = OverlayDetectorOutputFormat.BOXES_NORMALIZED,
            confidenceThreshold = 0.5f,
            minRegionAreaRatio = 0.0025f
        )

        assertEquals(1, regions.size)
        assertEquals(OverlayKind.CAPTION, regions.first().kind)
        assertTrue(regions.first().bottom >= 0.9f)
    }

    @Test
    fun `decode detector output builds region from heatmap`() {
        val heatmap = FloatArray(16 * 16)
        for (y in 11 until 14) {
            for (x in 2 until 14) {
                heatmap[(y * 16) + x] = 0.9f
            }
        }

        val regions = OverlayOnnxPostProcessor.decodeDetectorOutput(
            values = heatmap,
            shape = longArrayOf(1, 1, 16, 16),
            width = 1600,
            height = 900,
            outputFormat = OverlayDetectorOutputFormat.HEATMAP,
            confidenceThreshold = 0.5f,
            minRegionAreaRatio = 0.0025f
        )

        assertEquals(1, regions.size)
        assertTrue(regions.first().bottom > 0.7f)
        assertTrue(regions.first().kind == OverlayKind.CAPTION || regions.first().kind == OverlayKind.TEXT)
    }
}
