package com.duplicatefinder.presentation.screens.quality

import com.duplicatefinder.domain.model.ImageQualityItem
import com.duplicatefinder.domain.testImage
import org.junit.Assert.assertEquals
import org.junit.Test

class QualityReviewViewModelLogicTest {

    @Test
    fun `range expansion returns to earliest newly available undecided item`() {
        val low = qualityItem(id = 1, score = 12f)
        val mid = qualityItem(id = 2, score = 28f)
        val high = qualityItem(id = 3, score = 45f)

        val expandedItems = listOf(low, mid, high)

        val resolvedIndex = resolveCurrentIndexAfterRangeChange(
            items = expandedItems,
            currentId = high.image.id,
            kept = emptySet(),
            marked = emptySet(),
            moved = emptySet()
        )

        assertEquals(0, resolvedIndex)
        assertEquals(low.image.id, expandedItems[resolvedIndex].image.id)
    }

    @Test
    fun `range change keeps current item when no earlier undecided item exists`() {
        val low = qualityItem(id = 1, score = 12f)
        val high = qualityItem(id = 2, score = 45f)

        val resolvedIndex = resolveCurrentIndexAfterRangeChange(
            items = listOf(low, high),
            currentId = high.image.id,
            kept = setOf(low.image.id),
            marked = emptySet(),
            moved = emptySet()
        )

        assertEquals(1, resolvedIndex)
    }

    private fun qualityItem(id: Long, score: Float): ImageQualityItem = ImageQualityItem(
        image = testImage(id = id, size = 1_024L),
        qualityScore = score,
        sharpness = 0.5f,
        detailDensity = 0.5f,
        blockiness = 0.1f
    )
}
