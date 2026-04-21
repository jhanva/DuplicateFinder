package com.duplicatefinder.presentation.screens.resolution

import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.BaseTrashRepositoryFake
import com.duplicatefinder.domain.FakeSettingsRepository
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.usecase.MoveToTrashUseCase
import com.duplicatefinder.domain.usecase.ScanResolutionImagesUseCase
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
class ResolutionReviewViewModelTest {

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
        val viewModel = ResolutionReviewViewModel(
            settingsRepository = settingsRepository,
            imageRepository = imageRepository,
            scanResolutionImagesUseCase = ScanResolutionImagesUseCase(imageRepository, dispatcher),
            moveToTrashUseCase = MoveToTrashUseCase(object : BaseTrashRepositoryFake() {})
        )

        viewModel.startReview()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.requiresFolderSelection)
        assertEquals(emptyList<Long>(), state.resolutionItems.map { it.image.id })
    }

    @Test
    fun `scan completes with images and selects first item`() = runTest(dispatcher) {
        val small = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val large = testImage(id = 2, size = 200).copy(width = 1600, height = 1200)
        val viewModel = createViewModel(listOf(small, large))

        viewModel.startReview()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isScanning)
        assertEquals(2, state.resolutionItems.size)
        assertEquals(0, state.currentIndex)
        assertNotNull(state.currentItem)
        assertEquals(1L, state.currentItem?.image?.id)
    }

    @Test
    fun `keepCurrent advances to next undecided item`() = runTest(dispatcher) {
        val img1 = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val img2 = testImage(id = 2, size = 200).copy(width = 1600, height = 1200)
        val viewModel = createViewModel(listOf(img1, img2))

        viewModel.startReview()
        advanceUntilIdle()

        viewModel.keepCurrent()

        val state = viewModel.uiState.value
        assertEquals(setOf(1L), state.keptImageIds)
        assertEquals(2L, state.currentItem?.image?.id)
    }

    @Test
    fun `markCurrentForTrash advances to next undecided item`() = runTest(dispatcher) {
        val img1 = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val img2 = testImage(id = 2, size = 200).copy(width = 1600, height = 1200)
        val viewModel = createViewModel(listOf(img1, img2))

        viewModel.startReview()
        advanceUntilIdle()

        viewModel.markCurrentForTrash()

        val state = viewModel.uiState.value
        assertEquals(setOf(1L), state.markedForTrashIds)
        assertEquals(2L, state.currentItem?.image?.id)
    }

    @Test
    fun `applyBatchToTrash moves marked items`() = runTest(dispatcher) {
        val img1 = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val img2 = testImage(id = 2, size = 200).copy(width = 1600, height = 1200)
        val viewModel = createViewModel(listOf(img1, img2))

        viewModel.startReview()
        advanceUntilIdle()

        viewModel.markCurrentForTrash()
        viewModel.applyBatchToTrash()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isApplyingBatch)
        assertTrue(state.markedForTrashIds.isEmpty())
        assertEquals(setOf(1L), state.movedToTrashIds)
    }

    @Test
    fun `updateReviewMegapixelRange normalizes and resolves index`() = runTest(dispatcher) {
        val small = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val large = testImage(id = 2, size = 200).copy(width = 4000, height = 3000)
        val viewModel = createViewModel(listOf(small, large))

        viewModel.startReview()
        advanceUntilIdle()

        viewModel.updateReviewMegapixelRange(1f, 15f)

        val state = viewModel.uiState.value
        assertEquals(1f, state.reviewMegapixelMin, 0.01f)
        assertEquals(1, state.filteredResolutionItems.size)
        assertEquals(2L, state.currentItem?.image?.id)
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

    private fun createViewModel(images: List<ImageItem>): ResolutionReviewViewModel {
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
        return ResolutionReviewViewModel(
            settingsRepository = settingsRepository,
            imageRepository = imageRepository,
            scanResolutionImagesUseCase = ScanResolutionImagesUseCase(imageRepository, dispatcher),
            moveToTrashUseCase = MoveToTrashUseCase(object : BaseTrashRepositoryFake() {})
        )
    }
}
