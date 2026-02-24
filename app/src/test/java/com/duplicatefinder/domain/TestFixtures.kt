package com.duplicatefinder.domain

import android.net.Uri
import com.duplicatefinder.domain.model.CachedImageHashes
import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.FilterCriteria
import com.duplicatefinder.domain.model.ImageHashUpdate
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ScanMode
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.mockito.Mockito

fun testImage(
    id: Long,
    size: Long,
    dateModified: Long = id,
    name: String = "image_$id.jpg"
): ImageItem = ImageItem(
    id = id,
    uri = Mockito.mock(Uri::class.java),
    path = "/storage/$name",
    name = name,
    size = size,
    dateModified = dateModified,
    mimeType = "image/jpeg",
    width = 100,
    height = 100
)

open class BaseImageRepositoryFake : ImageRepository {
    override suspend fun getAllImages(): List<ImageItem> = emptyList()

    override fun scanImagesWithProgress(): Flow<ScanProgress> = flowOf()

    override suspend fun getImageById(id: Long): ImageItem? = null

    override suspend fun getImagesByIds(ids: List<Long>): List<ImageItem> = emptyList()

    override suspend fun calculateMd5Hash(image: ImageItem): String? = null

    override suspend fun calculatePerceptualHash(image: ImageItem): String? = null

    override suspend fun getCachedHashes(imageIds: List<Long>): Map<Long, CachedImageHashes> = emptyMap()

    override suspend fun saveHash(image: ImageItem, md5Hash: String, perceptualHash: String?) = Unit

    override suspend fun saveHashes(updates: List<ImageHashUpdate>) = Unit

    override suspend fun findExactDuplicates(images: List<ImageItem>): List<DuplicateGroup> = emptyList()

    override suspend fun findSimilarImages(
        images: List<ImageItem>,
        threshold: Float
    ): List<DuplicateGroup> = emptyList()

    override suspend fun applyFilter(
        groups: List<DuplicateGroup>,
        criteria: FilterCriteria
    ): List<DuplicateGroup> = groups

    override suspend fun deleteImages(images: List<ImageItem>): Result<Int> = Result.success(0)

    override suspend fun getFolders(): List<String> = emptyList()

    override suspend fun getImageCount(): Int = 0
}

class FakeSettingsRepository(
    threshold: Float = 0.9f
) : SettingsRepository {
    override val similarityThreshold: Flow<Float> = MutableStateFlow(threshold)
    override val autoDeleteDays: Flow<Int> = MutableStateFlow(30)
    override val isDarkMode: Flow<Boolean> = MutableStateFlow(false)
    override val excludedFolders: Flow<Set<String>> = MutableStateFlow(emptySet())
    override val lastScanTimestamp: Flow<Long> = MutableStateFlow(0L)
    override val scanMode: Flow<ScanMode> = MutableStateFlow(ScanMode.EXACT_AND_SIMILAR)

    override suspend fun setSimilarityThreshold(threshold: Float) = Unit

    override suspend fun setAutoDeleteDays(days: Int) = Unit

    override suspend fun setDarkMode(enabled: Boolean) = Unit

    override suspend fun setExcludedFolders(folders: Set<String>) = Unit

    override suspend fun setLastScanTimestamp(timestamp: Long) = Unit

    override suspend fun setScanMode(mode: ScanMode) = Unit
}
