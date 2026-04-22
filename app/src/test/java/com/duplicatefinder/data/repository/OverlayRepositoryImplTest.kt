package com.duplicatefinder.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.duplicatefinder.data.local.db.dao.OverlayDetectionDao
import com.duplicatefinder.data.media.MediaStoreDataSource
import com.duplicatefinder.domain.model.OverlayModelExecutionException
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayModelBundleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito
import java.io.File

class OverlayRepositoryImplTest {

    @Test
    fun `analyze bitmap does not silently fallback when active bundle inference fails`() {
        val runtime = Mockito.mock(OverlayOnnxRuntime::class.java)
        val bundleInfo = bundleInfo()
        val expected = OverlayModelExecutionException("Overlay model bundle failed during detection.")
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(runtime.analyze(bitmap, bundleInfo)).thenThrow(expected)

        val repository = OverlayRepositoryImpl(
            overlayDetectionDao = Mockito.mock(OverlayDetectionDao::class.java),
            context = Mockito.mock(Context::class.java),
            mediaStoreDataSource = Mockito.mock(MediaStoreDataSource::class.java),
            overlayModelBundleRepository = Mockito.mock(OverlayModelBundleRepository::class.java),
            overlayOnnxRuntime = runtime
        )

        try {
            repository.analyzeBitmap(bitmap, bundleInfo)
            fail("Expected OverlayModelExecutionException")
        } catch (error: OverlayModelExecutionException) {
            assertEquals(expected.message, error.message)
        }
    }

    private fun bundleInfo() = OverlayModelBundleInfo(
        bundleVersion = "overlay-bundle-v1",
        runtime = OverlayModelRuntime.ONNX_RUNTIME_ANDROID,
        textDetectorPath = "det.onnx",
        maskRefinerEncoderPath = "enc.onnx",
        maskRefinerDecoderPath = "dec.onnx",
        inputSizeTextDetector = 512,
        inputSizeMaskRefiner = 512
    )
}
