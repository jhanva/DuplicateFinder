package com.duplicatefinder.data.repository

import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.file.Files

class OverlayOnnxSessionCacheKeyTest {

    @Test
    fun `session cache key changes when model file is replaced`() {
        val modelFile = Files.createTempFile("overlay-model", ".onnx").toFile()
        modelFile.writeBytes(byteArrayOf(1, 2, 3))
        val firstKey = modelFile.toSessionCacheKey()

        Thread.sleep(5)
        modelFile.writeBytes(byteArrayOf(4, 5, 6, 7))
        val secondKey = modelFile.toSessionCacheKey()

        assertNotEquals(firstKey, secondKey)

        modelFile.delete()
    }
}
