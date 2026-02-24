package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.FakeSettingsRepository
import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.MatchType
import com.duplicatefinder.domain.model.ScanMode
import com.duplicatefinder.domain.testImage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindDuplicatesUseCaseTest {

    @Test
    fun `exact mode returns only exact groups ordered by potential savings`() = runTest {
        val exactHigh = duplicateGroup(
            id = "exact_high",
            images = listOf(testImage(1, 100), testImage(2, 100), testImage(3, 100)),
            matchType = MatchType.EXACT
        )
        val exactLow = duplicateGroup(
            id = "exact_low",
            images = listOf(testImage(4, 50), testImage(5, 50)),
            matchType = MatchType.EXACT
        )

        val repo = object : BaseImageRepositoryFake() {
            var similarCalled = false

            override suspend fun findExactDuplicates(images: List<ImageItem>): List<DuplicateGroup> {
                return listOf(exactLow, exactHigh)
            }

            override suspend fun findSimilarImages(
                images: List<ImageItem>,
                threshold: Float
            ): List<DuplicateGroup> {
                similarCalled = true
                return emptyList()
            }
        }

        val useCase = FindDuplicatesUseCase(repo, FakeSettingsRepository())
        val result = useCase(images = emptyList(), scanMode = ScanMode.EXACT)

        assertEquals(listOf("exact_high", "exact_low"), result.map { it.id })
        assertFalse(repo.similarCalled)
    }

    @Test
    fun `exact and similar mode merges overlapping groups and marks them as BOTH`() = runTest {
        val image1 = testImage(1, 100)
        val image2 = testImage(2, 100)
        val image3 = testImage(3, 80)
        val image4 = testImage(4, 60)

        val exact = duplicateGroup(
            id = "exact",
            images = listOf(image1, image2),
            matchType = MatchType.EXACT
        )
        val similarWithOverlap = duplicateGroup(
            id = "similar",
            images = listOf(image2, image3, image4),
            matchType = MatchType.SIMILAR
        )

        val repo = object : BaseImageRepositoryFake() {
            override suspend fun findExactDuplicates(images: List<ImageItem>): List<DuplicateGroup> {
                return listOf(exact)
            }

            override suspend fun findSimilarImages(
                images: List<ImageItem>,
                threshold: Float
            ): List<DuplicateGroup> {
                return listOf(similarWithOverlap)
            }
        }

        val useCase = FindDuplicatesUseCase(repo, FakeSettingsRepository(threshold = 0.93f))
        val result = useCase(images = emptyList(), scanMode = ScanMode.EXACT_AND_SIMILAR)

        assertEquals(1, result.size)
        val merged = result.first()
        assertEquals(MatchType.BOTH, merged.matchType)
        assertEquals(setOf(1L, 2L, 3L, 4L), merged.images.map { it.id }.toSet())
        assertEquals(340L, merged.totalSize)
        assertEquals(240L, merged.potentialSavings)
        assertTrue(merged.images.any { it.id == 3L })
        assertTrue(merged.images.any { it.id == 4L })
    }

    private fun duplicateGroup(
        id: String,
        images: List<ImageItem>,
        matchType: MatchType
    ): DuplicateGroup = DuplicateGroup(
        id = id,
        images = images,
        matchType = matchType,
        similarityScore = if (matchType == MatchType.EXACT) 1f else 0.95f,
        totalSize = images.sumOf { it.size },
        potentialSavings = images.drop(1).sumOf { it.size }
    )
}
