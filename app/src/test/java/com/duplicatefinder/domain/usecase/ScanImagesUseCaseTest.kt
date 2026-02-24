package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.BaseImageRepositoryFake
import com.duplicatefinder.domain.model.ImageHashUpdate
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ScanMode
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.testImage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Collections

class ScanImagesUseCaseTest {

    @Test
    fun `when there are no images emits loading hashing and complete with empty result`() = runTest {
        val repo = object : BaseImageRepositoryFake() {
            override suspend fun getAllImages(folders: Set<String>): List<ImageItem> = emptyList()
        }
        val useCase = ScanImagesUseCase(repo)

        val emissions = useCase(ScanMode.EXACT).toList()

        assertEquals(3, emissions.size)
        assertEquals(ScanPhase.LOADING, emissions[0].first.phase)
        assertEquals(ScanPhase.HASHING, emissions[1].first.phase)
        assertEquals(ScanPhase.COMPLETE, emissions[2].first.phase)
        assertEquals(emptyList<ImageItem>(), emissions[2].second)
    }

    @Test
    fun `exact mode hashes only repeated sizes and filters un-hashed images from final result`() = runTest {
        val image1 = testImage(id = 1, size = 100)
        val image2 = testImage(id = 2, size = 100)
        val image3 = testImage(id = 3, size = 50)

        val repo = object : BaseImageRepositoryFake() {
            val md5Requests = Collections.synchronizedList(mutableListOf<Long>())
            var savedUpdates: List<ImageHashUpdate> = emptyList()

            override suspend fun getAllImages(folders: Set<String>): List<ImageItem> = listOf(image1, image2, image3)

            override suspend fun calculateMd5Hash(image: ImageItem): String? {
                md5Requests.add(image.id)
                return "md5_${image.id}"
            }

            override suspend fun saveHashes(updates: List<ImageHashUpdate>) {
                savedUpdates = updates
            }
        }
        val useCase = ScanImagesUseCase(repo)

        val emissions = useCase(ScanMode.EXACT).toList()
        val finalImages = emissions.last().second

        assertEquals(setOf(1L, 2L), repo.md5Requests.toSet())
        assertEquals(2, finalImages.size)
        assertEquals(setOf(1L, 2L), finalImages.map { it.id }.toSet())
        assertNull(finalImages.firstOrNull { it.id == 1L }?.perceptualHash)
        assertEquals(2, repo.savedUpdates.size)
    }
}
