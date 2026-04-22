package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.BaseOverlayModelBundleRepositoryFake
import com.duplicatefinder.domain.BaseOverlayRepositoryFake
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelRuntime
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.testOverlayDetection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanOverlayCandidatesUseCaseTest {

    @Test
    fun `scan emits candidates sorted by refined score descending`() = runTest {
        val images = listOf(
            testImage(id = 1, size = 100),
            testImage(id = 2, size = 200),
            testImage(id = 3, size = 300)
        )
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = images.size
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = images.drop(offset).take(limit)
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            override suspend fun detectOverlayCandidates(
                images: List<com.duplicatefinder.domain.model.ImageItem>,
                modelVersion: String
            ) = listOf(
                testOverlayDetection(images[0], score = 0.40f, modelVersion = modelVersion),
                testOverlayDetection(images[1], score = 0.91f, modelVersion = modelVersion),
                testOverlayDetection(images[2], score = 0.73f, modelVersion = modelVersion)
            )
        }

        val states = ScanOverlayCandidatesUseCase(
            imageRepository,
            overlayRepository,
            BaseOverlayModelBundleRepositoryFake(),
            StandardTestDispatcher(testScheduler)
        )
            .invoke(folders = setOf("Camera"), reviewThreshold = 0.0f)
            .toList()

        assertEquals(listOf(2L, 3L, 1L), states.last().items.map { it.image.id })
    }

    @Test
    fun `scan reuses cached detections when cache is still valid`() = runTest {
        val images = listOf(testImage(id = 1, size = 100))
        val cached = testOverlayDetection(images.first())
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = images.size
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = images
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            override suspend fun getCachedDetections(
                imageIds: List<Long>,
                modelVersion: String
            ) = mapOf(cached.image.id to cached)
        }

        ScanOverlayCandidatesUseCase(
            imageRepository,
            overlayRepository,
            BaseOverlayModelBundleRepositoryFake(),
            StandardTestDispatcher(testScheduler)
        )
            .invoke(folders = setOf("Camera"))
            .toList()

        assertEquals(0, overlayRepository.detectCallCount)
    }

    @Test
    fun `scan excludes items below review threshold`() = runTest {
        val images = listOf(
            testImage(id = 1, size = 100),
            testImage(id = 2, size = 200)
        )
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = images.size
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = images
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            override suspend fun detectOverlayCandidates(
                images: List<com.duplicatefinder.domain.model.ImageItem>,
                modelVersion: String
            ) = listOf(
                testOverlayDetection(images[0], score = 0.40f, modelVersion = modelVersion),
                testOverlayDetection(images[1], score = 0.91f, modelVersion = modelVersion)
            )
        }

        val states = ScanOverlayCandidatesUseCase(
            imageRepository,
            overlayRepository,
            BaseOverlayModelBundleRepositoryFake(),
            StandardTestDispatcher(testScheduler)
        )
            .invoke(folders = setOf("Camera"), reviewThreshold = 0.60f)
            .toList()

        assertTrue(states.last().items.all { it.rankScore >= 0.60f })
        assertEquals(listOf(2L), states.last().items.map { it.image.id })
    }

    @Test
    fun `scan uses active overlay bundle version when available`() = runTest {
        val images = listOf(testImage(id = 1, size = 100))
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = images.size
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = images
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            var lastModelVersion: String? = null

            override suspend fun detectOverlayCandidates(
                images: List<com.duplicatefinder.domain.model.ImageItem>,
                modelVersion: String
            ): List<com.duplicatefinder.domain.model.OverlayDetection> {
                lastModelVersion = modelVersion
                return super.detectOverlayCandidates(images, modelVersion)
            }
        }
        val bundleRepository = BaseOverlayModelBundleRepositoryFake().apply {
            activeBundleInfo = OverlayModelBundleInfo(
                bundleVersion = "overlay-bundle-v9",
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

        ScanOverlayCandidatesUseCase(
            imageRepository,
            overlayRepository,
            bundleRepository,
            StandardTestDispatcher(testScheduler)
        )
            .invoke(folders = setOf("Camera"))
            .toList()

        assertEquals("overlay-bundle-v9", overlayRepository.lastModelVersion)
    }
}
