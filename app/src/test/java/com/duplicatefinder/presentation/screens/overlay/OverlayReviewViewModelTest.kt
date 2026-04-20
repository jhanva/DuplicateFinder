package com.duplicatefinder.presentation.screens.overlay

import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.BaseOverlayCleaningRepositoryFake
import com.duplicatefinder.domain.BaseOverlayModelBundleRepositoryFake
import com.duplicatefinder.domain.BaseOverlayRepositoryFake
import com.duplicatefinder.domain.BaseTrashRepositoryFake
import com.duplicatefinder.domain.FakeSettingsRepository
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.testOverlayDetection
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.usecase.ApplyOverlayPreviewDecisionUseCase
import com.duplicatefinder.domain.usecase.EnsureOverlayModelBundleUseCase
import com.duplicatefinder.domain.usecase.GenerateOverlayPreviewUseCase
import com.duplicatefinder.domain.usecase.MoveToTrashUseCase
import com.duplicatefinder.domain.usecase.ScanOverlayCandidatesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start review requires selected folders`() = runTest(dispatcher) {
        val settingsRepository = FakeSettingsRepository()
        val imageRepository = object : BaseImageRepositoryFake() {}
        val overlayRepository = object : BaseOverlayRepositoryFake() {}
        val viewModel = createViewModel(
            settingsRepository = settingsRepository,
            imageRepository = imageRepository,
            overlayRepository = overlayRepository
        )

        viewModel.startReview()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.requiresFolderSelection)
    }

    @Test
    fun `remove watermark generates preview for current item`() = runTest(dispatcher) {
        val image = testImage(id = 1, size = 100)
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = 1
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = listOf(image)
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            override suspend fun detectOverlayCandidates(
                images: List<com.duplicatefinder.domain.model.ImageItem>,
                modelVersion: String
            ) = listOf(testOverlayDetection(image, score = 0.95f, modelVersion = modelVersion))
        }
        val viewModel = createViewModel(
            imageRepository = imageRepository,
            overlayRepository = overlayRepository
        )

        viewModel.startReview()
        advanceUntilIdle()
        viewModel.generatePreviewForCurrent()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.previewState)
    }

    @Test
    fun `skip preview advances keeping original`() = runTest(dispatcher) {
        val image = testImage(id = 1, size = 100)
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = 1
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = listOf(image)
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            override suspend fun detectOverlayCandidates(
                images: List<com.duplicatefinder.domain.model.ImageItem>,
                modelVersion: String
            ) = listOf(testOverlayDetection(image, score = 0.95f, modelVersion = modelVersion))
        }
        val viewModel = createViewModel(
            imageRepository = imageRepository,
            overlayRepository = overlayRepository
        )

        viewModel.startReview()
        advanceUntilIdle()
        viewModel.generatePreviewForCurrent()
        advanceUntilIdle()
        viewModel.skipPreview()

        assertEquals(setOf(image.id), viewModel.uiState.value.skippedPreviewIds)
    }

    @Test
    fun `confirm cleaned replacement advances to next item`() = runTest(dispatcher) {
        val image = testImage(id = 1, size = 100)
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = 1
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = listOf(image)
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            override suspend fun detectOverlayCandidates(
                images: List<com.duplicatefinder.domain.model.ImageItem>,
                modelVersion: String
            ) = listOf(testOverlayDetection(image, score = 0.95f, modelVersion = modelVersion))
        }
        val viewModel = createViewModel(
            imageRepository = imageRepository,
            overlayRepository = overlayRepository
        )

        viewModel.startReview()
        advanceUntilIdle()
        viewModel.generatePreviewForCurrent()
        advanceUntilIdle()
        viewModel.keepCleanedPreview()

        assertEquals(setOf(image.id), viewModel.uiState.value.completedCleanReplaceIds)
    }

    private fun createViewModel(
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository().apply {
            kotlinx.coroutines.runBlocking { setScanFolders(setOf("Camera")) }
        },
        imageRepository: BaseImageRepositoryFake,
        overlayRepository: BaseOverlayRepositoryFake
    ): OverlayReviewViewModel {
        val bundleRepository = BaseOverlayModelBundleRepositoryFake().apply {
            activeBundleInfo = OverlayModelBundleInfo(
                bundleVersion = "test-model-v1",
                detectorStage1Path = "stage1.tflite",
                detectorStage2Path = "stage2.tflite",
                inpainterPath = "inpainter.tflite",
                inputSizeStage1 = 512,
                inputSizeStage2 = 512,
                inputSizeInpainter = 1024
            )
        }
        val cleaningRepository = BaseOverlayCleaningRepositoryFake()

        return OverlayReviewViewModel(
            settingsRepository = settingsRepository,
            imageRepository = imageRepository,
            scanOverlayCandidatesUseCase = ScanOverlayCandidatesUseCase(
                imageRepository,
                overlayRepository
            ),
            generateOverlayPreviewUseCase = GenerateOverlayPreviewUseCase(
                ensureOverlayModelBundleUseCase = EnsureOverlayModelBundleUseCase(bundleRepository),
                overlayCleaningRepository = cleaningRepository
            ),
            applyOverlayPreviewDecisionUseCase = ApplyOverlayPreviewDecisionUseCase(cleaningRepository),
            moveToTrashUseCase = MoveToTrashUseCase(object : BaseTrashRepositoryFake() {})
        )
    }
}
