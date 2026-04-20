package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseOverlayCleaningRepositoryFake
import com.duplicatefinder.domain.BaseOverlayModelBundleRepositoryFake
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.testOverlayDetection
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateOverlayPreviewUseCaseTest {

    @Test
    fun `generate preview fails fast when bundle is unavailable`() = runBlocking {
        val bundleRepository = BaseOverlayModelBundleRepositoryFake()
        val cleaningRepository = BaseOverlayCleaningRepositoryFake()
        val image = testImage(id = 1, size = 100)

        val result = GenerateOverlayPreviewUseCase(
            ensureOverlayModelBundleUseCase = EnsureOverlayModelBundleUseCase(bundleRepository),
            overlayCleaningRepository = cleaningRepository
        )(
            detection = testOverlayDetection(image),
            allowDownload = false
        )

        assertTrue(result.isFailure)
        assertEquals(0, cleaningRepository.generateCallCount)
    }

    @Test
    fun `generate preview delegates to cleaning repository when bundle is ready`() = runBlocking {
        val bundleRepository = BaseOverlayModelBundleRepositoryFake().apply {
            activeBundleInfo = bundleInfo()
        }
        val cleaningRepository = BaseOverlayCleaningRepositoryFake()
        val image = testImage(id = 1, size = 100)

        val result = GenerateOverlayPreviewUseCase(
            ensureOverlayModelBundleUseCase = EnsureOverlayModelBundleUseCase(bundleRepository),
            overlayCleaningRepository = cleaningRepository
        )(
            detection = testOverlayDetection(image),
            allowDownload = false
        )

        assertTrue(result.isSuccess)
        assertEquals(1, cleaningRepository.generateCallCount)
    }

    private fun bundleInfo() = OverlayModelBundleInfo(
        bundleVersion = "test-model-v1",
        detectorStage1Path = "stage1.tflite",
        detectorStage2Path = "stage2.tflite",
        inpainterPath = "inpainter.tflite",
        inputSizeStage1 = 512,
        inputSizeStage2 = 512,
        inputSizeInpainter = 1024
    )
}
