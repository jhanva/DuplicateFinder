package com.duplicatefinder.domain.repository

import com.duplicatefinder.domain.model.CachedImageHashes
import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.FilterCriteria
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    suspend fun getAllImages(): List<ImageItem>

    fun scanImagesWithProgress(): Flow<ScanProgress>

    suspend fun getImageById(id: Long): ImageItem?

    suspend fun getImagesByIds(ids: List<Long>): List<ImageItem>

    suspend fun calculateMd5Hash(image: ImageItem): String?

    suspend fun calculatePerceptualHash(image: ImageItem): String?

    suspend fun getCachedHashes(imageIds: List<Long>): Map<Long, CachedImageHashes>

    suspend fun saveHash(image: ImageItem, md5Hash: String, perceptualHash: String?)

    suspend fun findExactDuplicates(images: List<ImageItem>): List<DuplicateGroup>

    suspend fun findSimilarImages(
        images: List<ImageItem>,
        threshold: Float = 0.9f
    ): List<DuplicateGroup>

    suspend fun applyFilter(
        groups: List<DuplicateGroup>,
        criteria: FilterCriteria
    ): List<DuplicateGroup>

    suspend fun deleteImages(images: List<ImageItem>): Result<Int>

    suspend fun getFolders(): List<String>

    suspend fun getImageCount(): Int
}
