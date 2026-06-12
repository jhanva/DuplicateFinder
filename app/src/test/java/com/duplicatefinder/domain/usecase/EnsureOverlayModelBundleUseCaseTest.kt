package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseOverlayModelBundleRepositoryFake
import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EnsureOverlayModelBundleUseCaseTest {

    @Test
    fun `returns current bundle when already available`() = runBlocking {
        val repository = BaseOverlayModelBundleRepositoryFake().apply {
            activeBundleInfo = bundleInfo()
        }

        val result = EnsureOverlayModelBundleUseCase(repository)()

        assertEquals(EnsureOverlayModelBundleStatus.AVAILABLE, result.status)
        assertEquals(bundleInfo(), result.bundleInfo)
    }

    @Test
    fun `returns missing status when bundle is not installed locally`() = runBlocking {
        val repository = BaseOverlayModelBundleRepositoryFake()

        val result = EnsureOverlayModelBundleUseCase(repository)()

        assertEquals(EnsureOverlayModelBundleStatus.MISSING_BUNDLE, result.status)
        assertNotNull(result.errorMessage)
    }

    private fun bundleInfo() = OverlayModelBundleInfo(
        bundleVersion = "test-model-v1",
        runtime = OverlayModelRuntime.ONNX_RUNTIME_ANDROID,
        textDetectorPath = "ppocrv5_mobile_det.onnx",
        maskRefinerEncoderPath = "mobile_sam_encoder.onnx",
        maskRefinerDecoderPath = "mobile_sam_decoder.onnx",
        inputSizeTextDetector = 512,
        inputSizeMaskRefiner = 512
    )
}
