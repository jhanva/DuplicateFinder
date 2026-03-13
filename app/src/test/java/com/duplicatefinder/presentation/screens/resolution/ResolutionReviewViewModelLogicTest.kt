package com.duplicatefinder.presentation.screens.resolution

import com.duplicatefinder.domain.model.ResolutionReviewItem
import com.duplicatefinder.domain.testImage
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolutionReviewViewModelLogicTest {

    @Test
    fun `range expansion returns to earliest newly available undecided item`() {
        val low = resolutionItem(id = 1, width = 640, height = 480)
        val mid = resolutionItem(id = 2, width = 1600, height = 1200)
        val high = resolutionItem(id = 3, width = 2500, height = 2000)

        val expandedItems = listOf(low, mid, high)

        val resolvedIndex = resolveCurrentIndexAfterMegapixelRangeChange(
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
        val low = resolutionItem(id = 1, width = 640, height = 480)
        val high = resolutionItem(id = 2, width = 2500, height = 2000)

        val resolvedIndex = resolveCurrentIndexAfterMegapixelRangeChange(
            items = listOf(low, high),
            currentId = high.image.id,
            kept = setOf(low.image.id),
            marked = emptySet(),
            moved = emptySet()
        )

        assertEquals(1, resolvedIndex)
    }

    private fun resolutionItem(id: Long, width: Int, height: Int): ResolutionReviewItem {
        return ResolutionReviewItem.from(
            testImage(id = id, size = 1_024L).copy(width = width, height = height)
        )!!
    }
}
