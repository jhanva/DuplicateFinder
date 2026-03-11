package com.duplicatefinder.presentation.screens.quality

import com.duplicatefinder.domain.model.ImageQualityItem
import com.duplicatefinder.domain.testImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityReviewUiStateTest {

    @Test
    fun `range filters queue and current item follows filtered list`() {
        val low = qualityItem(id = 1, score = 22f)
        val mid = qualityItem(id = 2, score = 55f)
        val high = qualityItem(id = 3, score = 81f)

        val state = QualityReviewUiState(
            qualityItems = listOf(low, mid, high),
            reviewScoreMin = 30,
            reviewScoreMax = 60,
            currentIndex = 0
        )

        assertEquals(listOf(mid), state.filteredQualityItems)
        assertEquals(1, state.totalCount)
        assertNotNull(state.currentItem)
        assertEquals(mid.image.id, state.currentItem?.image?.id)
    }

    @Test
    fun `state reports when scanned items exist but range excludes all of them`() {
        val state = QualityReviewUiState(
            qualityItems = listOf(
                qualityItem(id = 1, score = 32f),
                qualityItem(id = 2, score = 48f)
            ),
            reviewScoreMin = 60,
            reviewScoreMax = 90
        )

        assertTrue(state.hasItems)
        assertTrue(state.hasNoFilterMatches)
        assertFalse(state.hasNoResults)
        assertEquals(2, state.filteredOutCount)
    }

    @Test
    fun `review can complete for visible queue even if hidden items remain undecided`() {
        val low = qualityItem(id = 1, score = 25f)
        val high = qualityItem(id = 2, score = 88f)

        val state = QualityReviewUiState(
            qualityItems = listOf(low, high),
            reviewScoreMin = 0,
            reviewScoreMax = 40,
            currentIndex = -1,
            keptImageIds = setOf(low.image.id)
        )

        assertEquals(1, state.reviewedCount)
        assertTrue(state.isReviewComplete)
    }

    private fun qualityItem(id: Long, score: Float): ImageQualityItem = ImageQualityItem(
        image = testImage(id = id, size = 1_024L),
        qualityScore = score,
        sharpness = 0.5f,
        detailDensity = 0.5f,
        blockiness = 0.1f
    )
}
