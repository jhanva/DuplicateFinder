package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseOverlayCleaningRepositoryFake
import com.duplicatefinder.domain.BaseOverlayCleaningModelRepositoryFake
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.testOverlayDetection
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.repository.OverlayOnnxInpainterContract
import com.duplicatefinder.domain.repository.OverlayOnnxRuntimeContract
import com.duplicatefinder.domain.repository.OverlayTensorRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateOverlayPreviewUseCaseTest {

    @Test
    fun `generate preview fails fast when bundle is unavailable`() = runBlocking {
        val cleaningModelRepository = BaseOverlayCleaningModelRepositoryFake()
        val cleaningRepository = BaseOverlayCleaningRepositoryFake()
        val image = testImage(id = 1, size = 100)

        val result = GenerateOverlayPreviewUseCase(
            ensureOverlayCleaningModelUseCase = EnsureOverlayCleaningModelUseCase(cleaningModelRepository),
            overlayCleaningRepository = cleaningRepository
        )(
            detection = testOverlayDetection(image),
            allowDownload = false
        )

        assertTrue(result.isFailure)
        assertEquals(0, cleaningRepository.generateCallCount)
    }

    @Test
    fun `generate preview exposes actionable error when bundle download is not configured`() = runBlocking {
        val cleaningModelRepository = BaseOverlayCleaningModelRepositoryFake().apply {
            downloadConfigured = false
        }
        val cleaningRepository = BaseOverlayCleaningRepositoryFake()
        val image = testImage(id = 1, size = 100)

        val result = GenerateOverlayPreviewUseCase(
            ensureOverlayCleaningModelUseCase = EnsureOverlayCleaningModelUseCase(cleaningModelRepository),
            overlayCleaningRepository = cleaningRepository
        )(
            detection = testOverlayDetection(image),
            allowDownload = true
        )

        assertTrue(result.isFailure)
        assertEquals(
            "Overlay cleaning model URL is not configured in this build.",
            result.exceptionOrNull()?.message
        )
        assertEquals(0, cleaningRepository.generateCallCount)
    }

    @Test
    fun `generate preview delegates to cleaning repository when bundle is ready`() = runBlocking {
        val cleaningModelRepository = BaseOverlayCleaningModelRepositoryFake().apply {
            activeModelInfo = modelInfo()
        }
        val cleaningRepository = BaseOverlayCleaningRepositoryFake()
        val image = testImage(id = 1, size = 100)

        val result = GenerateOverlayPreviewUseCase(
            ensureOverlayCleaningModelUseCase = EnsureOverlayCleaningModelUseCase(cleaningModelRepository),
            overlayCleaningRepository = cleaningRepository
        )(
            detection = testOverlayDetection(image),
            allowDownload = false
        )

        assertTrue(result.isSuccess)
        assertEquals(1, cleaningRepository.generateCallCount)
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
        onnx = OverlayOnnxRuntimeContract(
            inpainter = OverlayOnnxInpainterContract(
                tensorRange = OverlayTensorRange.NEGATIVE_ONE_TO_ONE
            )
        )
    )
}
