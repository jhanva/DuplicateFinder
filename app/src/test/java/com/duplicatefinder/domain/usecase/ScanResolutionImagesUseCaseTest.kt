package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.testImage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanResolutionImagesUseCaseTest {

    @Test
    fun `emits loading analyzing and complete with images sorted by pixel count`() = runTest {
        val small = testImage(id = 1, size = 100).copy(width = 640, height = 480)
        val large = testImage(id = 2, size = 200).copy(width = 4000, height = 3000)

        val repo = object : BaseImageRepositoryFake() {
            private val allImages = listOf(large, small)

            override suspend fun getImageCount(folders: Set<String>): Int = allImages.size

            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = allImages.drop(offset).take(limit)
        }

        val emissions = ScanResolutionImagesUseCase(repo)(setOf("Camera")).toList()

        assertEquals(4, emissions.size)
        assertEquals(ScanPhase.LOADING, emissions[0].progress.phase)
        assertEquals(ScanPhase.ANALYZING, emissions[1].progress.phase)
        assertEquals(ScanPhase.COMPLETE, emissions.last().progress.phase)
        assertEquals(listOf(1L, 2L), emissions.last().items.map { it.image.id })
    }

    @Test
    fun `filters images with invalid resolution metadata`() = runTest {
        val valid = testImage(id = 1, size = 100).copy(width = 1000, height = 1000)
        val invalid = testImage(id = 2, size = 100).copy(width = 0, height = 1200)

        val repo = object : BaseImageRepositoryFake() {
            private val allImages = listOf(valid, invalid)

            override suspend fun getImageCount(folders: Set<String>): Int = allImages.size

            override suspend fun getImagesBatch(
                folders: Set<String>,
                limit: Int,
                offset: Int
            ) = allImages.drop(offset).take(limit)
        }

        val emissions = ScanResolutionImagesUseCase(repo)(setOf("Camera")).toList()

        assertEquals(listOf(valid.id), emissions.last().items.map { it.image.id })
        assertEquals(2, emissions.last().progress.current)
    }
}
