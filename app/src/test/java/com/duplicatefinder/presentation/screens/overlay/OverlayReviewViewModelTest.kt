package com.duplicatefinder.presentation.screens.overlay

import android.content.IntentSender
import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.BaseOverlayModelBundleRepositoryFake
import com.duplicatefinder.domain.BaseOverlayRepositoryFake
import com.duplicatefinder.domain.BaseTrashRepositoryFake
import com.duplicatefinder.domain.FakeSettingsRepository
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.UserConfirmationRequiredException
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.testOverlayDetection
import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelRuntime
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
    private val requiredMessage = "Requires Samsung Gallery AI editing on a supported Samsung device."
    private val advisoryMessage =
        "Opens the image in Samsung Gallery. Tap Edit in Gallery to use AI tools like Object Eraser."
    private val launchFailedMessage = "Samsung Gallery could not be opened for this image."
    private val noChangesMessage = "No changes were detected in Samsung Gallery."

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deleteConfirmed = false
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
    fun `open in samsung gallery queues external edit intent and snapshots metadata`() = runTest(dispatcher) {
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
        viewModel.openCurrentInSamsungGallery()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val session = state.externalEditSession

        assertEquals(8, state.pendingExternalEditRequest?.specs?.size)
        assertEquals(image.mimeType, state.pendingExternalEditRequest?.specs?.firstOrNull()?.mimeType)
        assertEquals(
            "com.samsung.android.gallery.app.activity.GalleryActivity",
            state.pendingExternalEditRequest?.specs?.firstOrNull()?.className
        )
        assertTrue(state.canOpenInSamsungGallery)
        assertEquals(advisoryMessage, state.samsungGalleryHelperText)
        assertEquals(image.id, session?.imageId)
        assertEquals(image.size, session?.originalSize)
        assertEquals(image.dateModified, session?.originalDateModified)
    }

    @Test
    fun `returning from samsung gallery retries metadata lookup and advances when media store updates`() = runTest(dispatcher) {
        val originalImage = testImage(id = 1, size = 100, dateModified = 1)
        val editedImage = originalImage.copy(size = 200, dateModified = 2)
        val nextImage = testImage(id = 2, size = 150, dateModified = 3)
        var imageByIdCalls = 0
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = 2
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = listOf(originalImage, nextImage)
            override suspend fun getImageById(id: Long): ImageItem? {
                return when (id) {
                    originalImage.id -> {
                        imageByIdCalls += 1
                        if (imageByIdCalls >= 3) editedImage else originalImage
                    }
                    nextImage.id -> nextImage
                    else -> null
                }
            }
            override suspend fun getImagesByIds(ids: List<Long>) =
                listOf(originalImage, nextImage).filter { it.id in ids }
        }
        val overlayRepository = object : BaseOverlayRepositoryFake() {
            override suspend fun detectOverlayCandidates(
                images: List<com.duplicatefinder.domain.model.ImageItem>,
                modelVersion: String
            ) = listOf(
                testOverlayDetection(originalImage, score = 0.95f, modelVersion = modelVersion),
                testOverlayDetection(nextImage, score = 0.80f, modelVersion = modelVersion)
            )
        }
        val viewModel = createViewModel(
            imageRepository = imageRepository,
            overlayRepository = overlayRepository
        )

        viewModel.startReview()
        advanceUntilIdle()
        viewModel.openCurrentInSamsungGallery()
        advanceUntilIdle()
        viewModel.onExternalEditorLaunchConsumed()
        viewModel.onExternalEditorResult()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf(originalImage.id), state.editedInGalleryIds)
        assertEquals(nextImage.id, state.currentItem?.image?.id)
        assertEquals(null, state.externalEditSession)
        assertEquals(null, state.pendingExternalEditRequest)
        assertTrue(imageByIdCalls >= 3)
    }

    @Test
    fun `returning from samsung gallery without metadata changes keeps current item pending`() = runTest(dispatcher) {
        val image = testImage(id = 1, size = 100, dateModified = 1)
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
        viewModel.openCurrentInSamsungGallery()
        advanceUntilIdle()
        viewModel.onExternalEditorLaunchConsumed()
        viewModel.onExternalEditorResult()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(emptySet<Long>(), state.editedInGalleryIds)
        assertEquals(image.id, state.currentItem?.image?.id)
        assertEquals(null, state.pendingExternalEditRequest)
        assertEquals(noChangesMessage, state.error)
    }

    @Test
    fun `launch failure clears external edit session and reports gallery open error`() = runTest(dispatcher) {
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
        viewModel.openCurrentInSamsungGallery()
        advanceUntilIdle()
        viewModel.onExternalEditorLaunchFailed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.externalEditSession)
        assertEquals(null, state.pendingExternalEditRequest)
        assertEquals(launchFailedMessage, state.error)
    }

    @Test
    fun `batch trash confirmation still moves items after user approval`() = runTest(dispatcher) {
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
        val trashRepository = object : BaseTrashRepositoryFake() {
            override suspend fun moveToTrash(images: List<ImageItem>): Result<Int> {
                return if (!deleteConfirmed) {
                    Result.failure(UserConfirmationRequiredException(intentSender))
                } else {
                    Result.success(images.size)
                }
            }
        }
        val viewModel = createViewModel(
            imageRepository = imageRepository,
            overlayRepository = overlayRepository,
            trashRepository = trashRepository
        )

        viewModel.startReview()
        advanceUntilIdle()
        viewModel.markCurrentForTrash()
        viewModel.applyBatchToTrash()
        advanceUntilIdle()

        assertEquals(intentSender, viewModel.uiState.value.pendingDeleteIntentSender)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.movedToTrashIds)

        deleteConfirmed = true
        viewModel.onDeleteConfirmationResult(granted = true)
        advanceUntilIdle()

        assertEquals(setOf(image.id), viewModel.uiState.value.movedToTrashIds)
        assertEquals(null, viewModel.uiState.value.pendingDeleteIntentSender)
    }

    private var deleteConfirmed = false

    private fun createViewModel(
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository().apply {
            kotlinx.coroutines.runBlocking { setScanFolders(setOf("Camera")) }
        },
        imageRepository: BaseImageRepositoryFake,
        overlayRepository: BaseOverlayRepositoryFake,
        samsungGalleryEditIntentFactory: SamsungGalleryEditIntentFactory = SamsungGalleryEditIntentFactory(
            deviceManufacturer = "samsung",
            requiredMessage = requiredMessage,
            advisoryMessage = advisoryMessage,
            isSamsungGalleryInstalled = { true }
        ),
        trashRepository: BaseTrashRepositoryFake = object : BaseTrashRepositoryFake() {}
    ): OverlayReviewViewModel {
        val bundleRepository = BaseOverlayModelBundleRepositoryFake().apply {
            activeBundleInfo = overlayBundleInfo()
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
            samsungGalleryEditIntentFactory = samsungGalleryEditIntentFactory,
            samsungGalleryLaunchFailedMessage = launchFailedMessage,
            noGalleryChangesMessage = noChangesMessage,
            moveToTrashUseCase = MoveToTrashUseCase(trashRepository)
        )
    }

    private fun overlayBundleInfo() = OverlayModelBundleInfo(
        bundleVersion = "test-model-v1",
        runtime = OverlayModelRuntime.ONNX_RUNTIME_ANDROID,
        textDetectorPath = "ppocrv5_mobile_det.onnx",
        maskRefinerEncoderPath = "mobile_sam_encoder.onnx",
        maskRefinerDecoderPath = "mobile_sam_decoder.onnx",
        inputSizeTextDetector = 512,
        inputSizeMaskRefiner = 512
    )
}
