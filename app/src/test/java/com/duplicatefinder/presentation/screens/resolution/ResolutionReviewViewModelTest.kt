package com.duplicatefinder.presentation.screens.resolution

import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.BaseTrashRepositoryFake
import com.duplicatefinder.domain.FakeSettingsRepository
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
            scanResolutionImagesUseCase = ScanResolutionImagesUseCase(imageRepository),
            moveToTrashUseCase = MoveToTrashUseCase(object : BaseTrashRepositoryFake() {})
        )

        viewModel.startReview()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.requiresFolderSelection)
        assertEquals(emptyList<Long>(), state.resolutionItems.map { it.image.id })
    }
}
