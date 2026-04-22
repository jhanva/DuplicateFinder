package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseOverlayModelBundleRepositoryFake
import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EnsureOverlayModelBundleUseCaseTest {

    @Test
    fun `returns current bundle when already available`() = runBlocking {
        val repository = BaseOverlayModelBundleRepositoryFake().apply {
            activeBundleInfo = bundleInfo()
        }

        val result = EnsureOverlayModelBundleUseCase(repository)(allowDownload = false)

        assertEquals(EnsureOverlayModelBundleStatus.AVAILABLE, result.status)
    }

    @Test
    fun `downloads bundle when missing and download is allowed`() = runBlocking {
        val repository = BaseOverlayModelBundleRepositoryFake().apply {
            downloadConfigured = true
            downloadResult = Result.success(bundleInfo())
        }

        val result = EnsureOverlayModelBundleUseCase(repository)(allowDownload = true)

        assertEquals(EnsureOverlayModelBundleStatus.DOWNLOADED, result.status)
    }

    @Test
    fun `returns missing status when bundle url is not configured`() = runBlocking {
        val repository = BaseOverlayModelBundleRepositoryFake()

        val result = EnsureOverlayModelBundleUseCase(repository)(allowDownload = false)

        assertEquals(EnsureOverlayModelBundleStatus.MISSING_CONFIGURATION, result.status)
    }

    @Test
    fun `returns missing status when download is requested but manifest is not configured`() = runBlocking {
        val repository = BaseOverlayModelBundleRepositoryFake().apply {
            downloadConfigured = false
        }

        val result = EnsureOverlayModelBundleUseCase(repository)(allowDownload = true)

        assertEquals(EnsureOverlayModelBundleStatus.MISSING_CONFIGURATION, result.status)
    }

    private fun bundleInfo() = OverlayModelBundleInfo(
        bundleVersion = "test-model-v1",
        runtime = OverlayModelRuntime.ONNX_RUNTIME_ANDROID,
        textDetectorPath = "ppocrv5_mobile_det.onnx",
        maskRefinerEncoderPath = "mobile_sam_encoder.onnx",
        maskRefinerDecoderPath = "mobile_sam_decoder.onnx",
        inpainterPath = "aot_gan.onnx",
        inputSizeTextDetector = 512,
        inputSizeMaskRefiner = 512,
        inputSizeInpainter = 1024
    )
}
