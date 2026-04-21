package com.duplicatefinder.presentation.screens.quality

import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.BaseQualityRepositoryFake
import com.duplicatefinder.domain.BaseTrashRepositoryFake
import com.duplicatefinder.domain.FakeSettingsRepository
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ImageQualityMetrics
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.usecase.MoveToTrashUseCase
import com.duplicatefinder.domain.usecase.ScanQualityImagesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QualityReviewViewModelTest {

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
        val qualityRepository = object : BaseQualityRepositoryFake() {}
        val viewModel = QualityReviewViewModel(
            settingsRepository = settingsRepository,
            imageRepository = imageRepository,
            scanQualityImagesUseCase = ScanQualityImagesUseCase(
                imageRepository,
                qualityRepository,
                dispatcher
            ),
            moveToTrashUseCase = MoveToTrashUseCase(object : BaseTrashRepositoryFake() {})
        )

        viewModel.startReview()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.requiresFolderSelection)
        assertEquals(emptyList<Long>(), state.qualityItems.map { it.image.id })
    }

    @Test
    fun `scan completes with images and selects first item`() = runTest(dispatcher) {
        val img1 = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val img2 = testImage(id = 2, size = 200).copy(width = 1600, height = 1200)
        val viewModel = createViewModel(listOf(img1, img2))

        viewModel.startReview()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isScanning)
        assertEquals(2, state.qualityItems.size)
        assertEquals(0, state.currentIndex)
        assertNotNull(state.currentItem)
    }

    @Test
    fun `keepCurrent advances to next undecided item`() = runTest(dispatcher) {
        val img1 = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val img2 = testImage(id = 2, size = 200).copy(width = 1600, height = 1200)
        val viewModel = createViewModel(listOf(img1, img2))

        viewModel.startReview()
        advanceUntilIdle()

        val firstItemId = viewModel.uiState.value.currentItem!!.image.id
        viewModel.keepCurrent()

        val state = viewModel.uiState.value
        assertEquals(setOf(firstItemId), state.keptImageIds)
        assertNotNull(state.currentItem)
    }

    @Test
    fun `markCurrentForTrash advances to next undecided item`() = runTest(dispatcher) {
        val img1 = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val img2 = testImage(id = 2, size = 200).copy(width = 1600, height = 1200)
        val viewModel = createViewModel(listOf(img1, img2))

        viewModel.startReview()
        advanceUntilIdle()

        val firstItemId = viewModel.uiState.value.currentItem!!.image.id
        viewModel.markCurrentForTrash()

        val state = viewModel.uiState.value
        assertEquals(setOf(firstItemId), state.markedForTrashIds)
        assertNotNull(state.currentItem)
    }

    @Test
    fun `applyBatchToTrash moves marked items`() = runTest(dispatcher) {
        val img1 = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val img2 = testImage(id = 2, size = 200).copy(width = 1600, height = 1200)
        val viewModel = createViewModel(listOf(img1, img2))

        viewModel.startReview()
        advanceUntilIdle()

        val firstItemId = viewModel.uiState.value.currentItem!!.image.id
        viewModel.markCurrentForTrash()
        viewModel.applyBatchToTrash()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isApplyingBatch)
        assertTrue(state.markedForTrashIds.isEmpty())
        assertEquals(setOf(firstItemId), state.movedToTrashIds)
    }

    @Test
    fun `review completes when all filtered items are decided`() = runTest(dispatcher) {
        val img = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val viewModel = createViewModel(listOf(img))

        viewModel.startReview()
        advanceUntilIdle()

        viewModel.keepCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isReviewComplete)
        assertNull(state.currentItem)
    }

    private val defaultMetrics = ImageQualityMetrics(
        sharpness = 0.5f,
        detailDensity = 0.5f,
        blockiness = 0.1f
    )

    private fun createViewModel(images: List<ImageItem>): QualityReviewViewModel {
        val settingsRepository = FakeSettingsRepository().apply {
            kotlinx.coroutines.runBlocking { setScanFolders(setOf("Camera")) }
        }
        val imageRepository = object : BaseImageRepositoryFake() {
            override suspend fun getImageCount(folders: Set<String>): Int = images.size
            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ): List<ImageItem> = images.drop(offset).take(limit)
        }
        val qualityRepository = object : BaseQualityRepositoryFake() {
            override suspend fun calculateQualityMetrics(image: ImageItem): ImageQualityMetrics =
                defaultMetrics
        }
        return QualityReviewViewModel(
            settingsRepository = settingsRepository,
            imageRepository = imageRepository,
            scanQualityImagesUseCase = ScanQualityImagesUseCase(
                imageRepository,
                qualityRepository,
                dispatcher
            ),
            moveToTrashUseCase = MoveToTrashUseCase(object : BaseTrashRepositoryFake() {})
        )
    }
}
