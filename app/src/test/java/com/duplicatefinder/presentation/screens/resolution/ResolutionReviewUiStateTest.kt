package com.duplicatefinder.presentation.screens.resolution

import com.duplicatefinder.domain.model.ResolutionReviewItem
import com.duplicatefinder.domain.testImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionReviewUiStateTest {

    @Test
    fun `megapixel range filters queue and current item follows filtered list`() {
        val low = resolutionItem(id = 1, width = 640, height = 480)
        val mid = resolutionItem(id = 2, width = 1600, height = 1200)
        val high = resolutionItem(id = 3, width = 4000, height = 3000)

        val state = ResolutionReviewUiState(
            resolutionItems = listOf(low, mid, high),
            reviewMegapixelMin = 1f,
            reviewMegapixelMax = 3f,
            currentIndex = 0
        )

        assertEquals(listOf(mid), state.filteredResolutionItems)
        assertEquals(1, state.totalCount)
        assertNotNull(state.currentItem)
        assertEquals(mid.image.id, state.currentItem?.image?.id)
    }

    @Test
    fun `state reports when scanned items exist but range excludes all of them`() {
        val state = ResolutionReviewUiState(
            resolutionItems = listOf(
                resolutionItem(id = 1, width = 640, height = 480),
                resolutionItem(id = 2, width = 1280, height = 720)
            ),
            reviewMegapixelMin = 2.5f,
            reviewMegapixelMax = 3f
        )

        assertTrue(state.hasItems)
        assertTrue(state.hasNoFilterMatches)
        assertFalse(state.hasNoResults)
        assertEquals(2, state.filteredOutCount)
    }

    @Test
    fun `review can complete for visible queue even if hidden items remain undecided`() {
        val low = resolutionItem(id = 1, width = 640, height = 480)
        val high = resolutionItem(id = 2, width = 4000, height = 3000)

        val state = ResolutionReviewUiState(
            resolutionItems = listOf(low, high),
            reviewMegapixelMin = 0f,
            reviewMegapixelMax = 0.5f,
            currentIndex = -1,
            keptImageIds = setOf(low.image.id)
        )

        assertEquals(1, state.reviewedCount)
        assertTrue(state.isReviewComplete)
    }

    private fun resolutionItem(id: Long, width: Int, height: Int): ResolutionReviewItem {
        return ResolutionReviewItem.from(
            testImage(id = id, size = 1_024L).copy(width = width, height = height)
        )!!
    }
}
