package com.duplicatefinder.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.model.OverlayModelExecutionException
import com.duplicatefinder.domain.model.OverlayRegion
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.TrashRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito
import java.nio.file.Files

class OverlayCleaningRepositoryImplTest {

    @Test
    fun `render preview bitmap propagates model failure instead of falling back`() {
        val runtime = Mockito.mock(OverlayOnnxRuntime::class.java)
        val bundleInfo = bundleInfo()
        val sourceBitmap = Mockito.mock(Bitmap::class.java)
        val regions = listOf(
            OverlayRegion(
                left = 0.1f,
                top = 0.1f,
                right = 0.5f,
                bottom = 0.3f,
                confidence = 0.9f,
                kind = OverlayKind.TEXT
            )
        )
        val expected = OverlayModelExecutionException("Overlay model bundle failed during cleaning.")
        Mockito.`when`(runtime.inpaint(sourceBitmap, regions, bundleInfo)).thenThrow(expected)

        val previewDir = Files.createTempDirectory("overlay-preview-test").toFile()
        val bundleDir = Files.createTempDirectory("overlay-bundle-test").toFile()
        val repository = OverlayCleaningRepositoryImpl(
            context = Mockito.mock(Context::class.java),
            trashRepository = Mockito.mock(TrashRepository::class.java),
            overlayOnnxRuntime = runtime,
            previewDir = previewDir,
            bundleDir = bundleDir
        )

        try {
            repository.renderPreviewBitmap(sourceBitmap, regions, bundleInfo)
            fail("Expected OverlayModelExecutionException")
        } catch (error: OverlayModelExecutionException) {
            assertEquals(expected.message, error.message)
        } finally {
            previewDir.deleteRecursively()
            bundleDir.deleteRecursively()
        }
    }

    private fun bundleInfo() = OverlayModelBundleInfo(
        bundleVersion = "overlay-bundle-v1",
        runtime = OverlayModelRuntime.ONNX_RUNTIME_ANDROID,
        textDetectorPath = "det.onnx",
        maskRefinerEncoderPath = "enc.onnx",
        maskRefinerDecoderPath = "dec.onnx",
        inpainterPath = "inp.onnx",
        inputSizeTextDetector = 512,
        inputSizeMaskRefiner = 512,
        inputSizeInpainter = 1024
    )
}
