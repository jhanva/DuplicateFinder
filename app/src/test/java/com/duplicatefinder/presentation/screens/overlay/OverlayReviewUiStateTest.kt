package com.duplicatefinder.presentation.screens.overlay

import com.duplicatefinder.domain.testCleaningPreview
import com.duplicatefinder.domain.testImage
import com.duplicatefinder.domain.testOverlayDetection
import com.duplicatefinder.domain.model.OverlayReviewItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReviewUiStateTest {

    @Test
    fun `items are filtered by score range and sorted by rank score descending`() {
        val items = listOf(
            overlayItem(id = 2, score = 0.80f),
            overlayItem(id = 3, score = 0.30f),
            overlayItem(id = 1, score = 0.95f)
        )

        val state = OverlayReviewUiState(
            overlayItems = items,
            minOverlayScore = 0.50f,
            maxOverlayScore = 1.00f
        )

        assertEquals(listOf(1L, 2L), state.filteredOverlayItems.map { it.image.id })
    }

    @Test
    fun `review is complete when current item is null and scanning is finished`() {
        val item = overlayItem(id = 1, score = 0.95f)
        val state = OverlayReviewUiState(
            isScanning = false,
            overlayItems = listOf(item),
            currentIndex = -1,
            keptImageIds = setOf(item.image.id)
        )

        assertTrue(state.isReviewComplete)
    }

    @Test
    fun `preview ready state exposes the generated preview`() {
        val image = testImage(id = 1, size = 1_024L)
        val state = OverlayReviewUiState(
            previewState = testCleaningPreview(image)
        )

        assertNotNull(state.previewState?.previewUri)
        assertTrue(state.hasReadyPreview)
    }

    private fun overlayItem(id: Long, score: Float): OverlayReviewItem {
        val image = testImage(id = id, size = id * 100)
        return OverlayReviewItem(
            image = image,
            detection = testOverlayDetection(image, score = score),
            rankScore = score
        )
    }
}
