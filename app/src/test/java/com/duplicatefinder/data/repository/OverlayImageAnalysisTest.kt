package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.model.OverlayRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayImageAnalysisTest {

    @Test
    fun `analyze detects caption-like overlay from image content`() {
        val width = 80
        val height = 60
        val pixels = IntArray(width * height) { solidColor(120, 125, 130) }

        for (y in 44 until 54) {
            for (x in 8 until 72) {
                val stripe = ((x / 3) + (y / 2)) % 2 == 0
                pixels[(y * width) + x] = if (stripe) {
                    solidColor(242, 242, 242)
                } else {
                    solidColor(18, 18, 18)
                }
            }
        }

        val result = OverlayImageAnalysis.analyze(pixels, width, height)

        assertTrue(result.refinedScore >= 0.55f)
        assertTrue(result.regions.isNotEmpty())
        assertTrue(result.overlayKinds.any { it == OverlayKind.CAPTION || it == OverlayKind.TEXT })
        assertTrue(result.regions.any { it.bottom >= 0.7f })
    }

    @Test
    fun `analyze keeps plain image below review threshold`() {
        val width = 80
        val height = 60
        val pixels = IntArray(width * height) { solidColor(100, 110, 120) }

        val result = OverlayImageAnalysis.analyze(pixels, width, height)

        assertTrue(result.refinedScore < 0.4f)
        assertTrue(result.regions.isEmpty())
        assertEquals(setOf(OverlayKind.UNKNOWN), result.overlayKinds)
    }

    @Test
    fun `clean overlay updates only masked region`() {
        val width = 40
        val height = 30
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            solidColor(70 + x, 90 + y, 110 + (x / 2))
        }
        val region = OverlayRegion(
            left = 0.25f,
            top = 0.2f,
            right = 0.75f,
            bottom = 0.5f,
            confidence = 0.9f,
            kind = OverlayKind.TEXT
        )

        val maskedIndexes = mutableListOf<Int>()
        for (y in 6 until 15) {
            for (x in 10 until 30) {
                val index = (y * width) + x
                pixels[index] = solidColor(255, 255, 255)
                maskedIndexes += index
            }
        }

        val cleaned = OverlayImageAnalysis.cleanOverlay(
            pixels = pixels,
            width = width,
            height = height,
            regions = listOf(region)
        )

        assertFalse(maskedIndexes.all { cleaned[it] == pixels[it] })
        assertEquals(pixels[0], cleaned[0])
        assertEquals(pixels[(height - 1) * width], cleaned[(height - 1) * width])
    }

    private fun solidColor(red: Int, green: Int, blue: Int): Int {
        return (255 shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }
}
