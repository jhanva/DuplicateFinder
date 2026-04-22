package com.duplicatefinder.presentation.screens.overlay

import android.content.IntentSender
import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.BaseOverlayCleaningRepositoryFake
import com.duplicatefinder.domain.BaseOverlayModelBundleRepositoryFake
import com.duplicatefinder.domain.BaseOverlayRepositoryFake
import com.duplicatefinder.domain.BaseTrashRepositoryFake
import com.duplicatefinder.domain.FakeSettingsRepository
import com.duplicatefinder.domain.model.CleaningPreview
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.OverlayPreviewDecision
import com.duplicatefinder.domain.model.UserConfirmationRequiredException
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.testOverlayDetection
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelRuntime
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
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deleteConfirmed = false
        applyDeleteCalls = 0
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
            override suspend fun getImageById(id: Long) = image.takeIf { it.id == id }
            override suspend fun getImagesByIds(ids: List<Long>) = listOf(image).filter { it.id in ids }
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
    fun `remove watermark is blocked for unsupported image formats`() = runTest(dispatcher) {
        val image = testImage(id = 1, size = 100, name = "image_1.gif").copy(
            mimeType = "image/gif"
        )
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = 1
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = listOf(image)
            override suspend fun getImageById(id: Long) = image.takeIf { it.id == id }
            override suspend fun getImagesByIds(ids: List<Long>) = listOf(image).filter { it.id in ids }
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            override suspend fun detectOverlayCandidates(
                images: List<com.duplicatefinder.domain.model.ImageItem>,
                modelVersion: String
            ) = listOf(testOverlayDetection(image, score = 0.95f, modelVersion = modelVersion))
        }
        val bundleRepository = BaseOverlayModelBundleRepositoryFake().apply {
            activeBundleInfo = OverlayModelBundleInfo(
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
        val cleaningRepository = BaseOverlayCleaningRepositoryFake()
        val viewModel = OverlayReviewViewModel(
            settingsRepository = FakeSettingsRepository().apply {
                kotlinx.coroutines.runBlocking { setScanFolders(setOf("Camera")) }
            },
            imageRepository = imageRepository,
            scanOverlayCandidatesUseCase = ScanOverlayCandidatesUseCase(
                imageRepository,
                overlayRepository,
                bundleRepository,
                dispatcher
            ),
            generateOverlayPreviewUseCase = GenerateOverlayPreviewUseCase(
                ensureOverlayModelBundleUseCase = EnsureOverlayModelBundleUseCase(bundleRepository),
                overlayCleaningRepository = cleaningRepository
            ),
            applyOverlayPreviewDecisionUseCase = ApplyOverlayPreviewDecisionUseCase(cleaningRepository),
            moveToTrashUseCase = MoveToTrashUseCase(object : BaseTrashRepositoryFake() {})
        )

        viewModel.startReview()
        advanceUntilIdle()
        viewModel.generatePreviewForCurrent()
        advanceUntilIdle()

        assertEquals(0, cleaningRepository.generateCallCount)
        assertEquals(null, viewModel.uiState.value.previewState)
        assertTrue(viewModel.uiState.value.error?.contains("not supported", ignoreCase = true) == true)
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
            override suspend fun getImageById(id: Long) = image.takeIf { it.id == id }
            override suspend fun getImagesByIds(ids: List<Long>) = listOf(image).filter { it.id in ids }
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
        advanceUntilIdle()

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
            override suspend fun getImageById(id: Long) = image.takeIf { it.id == id }
            override suspend fun getImagesByIds(ids: List<Long>) = listOf(image).filter { it.id in ids }
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
        advanceUntilIdle()

        assertEquals(setOf(image.id), viewModel.uiState.value.completedCleanReplaceIds)
    }

    @Test
    fun `delete all keeps preview pending until user confirms and then completes`() = runTest(dispatcher) {
        val image = testImage(id = 1, size = 100)
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = 1
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = listOf(image)
            override suspend fun getImageById(id: Long) = image.takeIf { it.id == id }
            override suspend fun getImagesByIds(ids: List<Long>) =
                if (ids.contains(image.id) && deleteConfirmed) {
                    emptyList()
                } else {
                    listOf(image).filter { it.id in ids }
                }
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            override suspend fun detectOverlayCandidates(
                images: List<com.duplicatefinder.domain.model.ImageItem>,
                modelVersion: String
            ) = listOf(testOverlayDetection(image, score = 0.95f, modelVersion = modelVersion))
        }
        val intentSender = Mockito.mock(IntentSender::class.java)
        val cleaningRepository = object : BaseOverlayCleaningRepositoryFake() {
            override suspend fun applyDecision(
                image: ImageItem,
                preview: CleaningPreview,
                decision: OverlayPreviewDecision
            ): Result<Unit> {
                lastDecision = decision
                applyDeleteCalls += 1
                return if (decision == OverlayPreviewDecision.DELETE_ALL && !deleteConfirmed) {
                    Result.failure(UserConfirmationRequiredException(intentSender))
                } else {
                    Result.success(Unit)
                }
            }
        }
        val viewModel = createViewModel(
            imageRepository = imageRepository,
            overlayRepository = overlayRepository,
            cleaningRepository = cleaningRepository
        )

        viewModel.startReview()
        advanceUntilIdle()
        viewModel.generatePreviewForCurrent()
        advanceUntilIdle()
        viewModel.deleteAllFromPreview()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.previewState)
        assertEquals(intentSender, viewModel.uiState.value.pendingDeleteIntentSender)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.movedToTrashIds)

        deleteConfirmed = true
        viewModel.onDeleteConfirmationResult(granted = true)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.previewState)
        assertEquals(setOf(image.id), viewModel.uiState.value.movedToTrashIds)
        assertEquals(null, viewModel.uiState.value.pendingDeleteIntentSender)
        assertEquals(2, applyDeleteCalls)
    }

    private var deleteConfirmed = false
    private var applyDeleteCalls = 0

    private fun createViewModel(
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository().apply {
            kotlinx.coroutines.runBlocking { setScanFolders(setOf("Camera")) }
        },
        imageRepository: BaseImageRepositoryFake,
        overlayRepository: BaseOverlayRepositoryFake,
        cleaningRepository: BaseOverlayCleaningRepositoryFake = BaseOverlayCleaningRepositoryFake()
    ): OverlayReviewViewModel {
        val bundleRepository = BaseOverlayModelBundleRepositoryFake().apply {
            activeBundleInfo = OverlayModelBundleInfo(
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

        return OverlayReviewViewModel(
            settingsRepository = settingsRepository,
            imageRepository = imageRepository,
            scanOverlayCandidatesUseCase = ScanOverlayCandidatesUseCase(
                imageRepository,
                overlayRepository,
                bundleRepository,
                dispatcher
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
