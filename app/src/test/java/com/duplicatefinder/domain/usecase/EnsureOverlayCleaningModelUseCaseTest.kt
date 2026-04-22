package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseOverlayCleaningModelRepositoryFake
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayTensorRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EnsureOverlayCleaningModelUseCaseTest {

    @Test
    fun `returns current cleaning model when already available`() = runBlocking {
        val repository = BaseOverlayCleaningModelRepositoryFake().apply {
            activeModelInfo = modelInfo()
        }

        val result = EnsureOverlayCleaningModelUseCase(repository)(allowDownload = false)

        assertEquals(EnsureOverlayCleaningModelStatus.AVAILABLE, result.status)
        assertEquals(0, repository.downloadCallCount)
    }

    @Test
    fun `downloads cleaning model when missing and download is allowed`() = runBlocking {
        val repository = BaseOverlayCleaningModelRepositoryFake().apply {
            downloadConfigured = true
            downloadResult = Result.success(modelInfo())
        }

        val result = EnsureOverlayCleaningModelUseCase(repository)(allowDownload = true)

        assertEquals(EnsureOverlayCleaningModelStatus.DOWNLOADED, result.status)
        assertEquals(1, repository.downloadCallCount)
    }

    @Test
    fun `returns missing status when cleaning model url is not configured`() = runBlocking {
        val repository = BaseOverlayCleaningModelRepositoryFake()

        val result = EnsureOverlayCleaningModelUseCase(repository)(allowDownload = true)

        assertEquals(EnsureOverlayCleaningModelStatus.MISSING_CONFIGURATION, result.status)
        assertEquals(0, repository.downloadCallCount)
    }

    private fun modelInfo() = OverlayModelBundleInfo(
        bundleVersion = "overlay-cleaning-aot-gan-v1",
        runtime = OverlayModelRuntime.ONNX_RUNTIME_ANDROID,
        textDetectorPath = "",
        maskRefinerEncoderPath = "",
        maskRefinerDecoderPath = "",
        inpainterPath = "AOT-GAN.onnx",
        inputSizeTextDetector = 0,
        inputSizeMaskRefiner = 0,
        inputSizeInpainter = 512,
        onnx = com.duplicatefinder.domain.repository.OverlayOnnxRuntimeContract(
            inpainter = com.duplicatefinder.domain.repository.OverlayOnnxInpainterContract(
                tensorRange = OverlayTensorRange.NEGATIVE_ONE_TO_ONE
            )
        )
    )
}
