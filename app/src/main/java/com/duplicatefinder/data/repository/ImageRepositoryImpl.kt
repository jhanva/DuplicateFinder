package com.duplicatefinder.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.duplicatefinder.data.local.db.dao.ImageHashDao
import com.duplicatefinder.data.local.db.entities.ImageHashEntity
import com.duplicatefinder.data.media.MediaStoreDataSource
import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.FilterCriteria
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.MatchType
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.util.hash.MD5HashCalculator
import com.duplicatefinder.util.hash.PerceptualHashCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val imageHashDao: ImageHashDao,
    private val md5HashCalculator: MD5HashCalculator,
    private val perceptualHashCalculator: PerceptualHashCalculator
) : ImageRepository {

    private val contentResolver: ContentResolver = context.contentResolver

    override suspend fun getAllImages(): List<ImageItem> {
        return mediaStoreDataSource.getAllImages()
    }

    override fun scanImagesWithProgress(): Flow<ScanProgress> = flow {
        emit(ScanProgress(ScanPhase.LOADING, 0, 0))

        val images = mediaStoreDataSource.getAllImages()
        val total = images.size

        emit(ScanProgress(ScanPhase.HASHING, 0, total))

        images.forEachIndexed { index, image ->
            emit(
                ScanProgress(
                    phase = ScanPhase.HASHING,
                    current = index + 1,
                    total = total,
                    currentFile = image.name
                )
            )
        }

        emit(ScanProgress(ScanPhase.COMPLETE, total, total))
    }.flowOn(Dispatchers.IO)

    override suspend fun getImageById(id: Long): ImageItem? {
        return mediaStoreDataSource.getImageById(id)
    }

    override suspend fun getImagesByIds(ids: List<Long>): List<ImageItem> {
        return ids.mapNotNull { mediaStoreDataSource.getImageById(it) }
    }

    override suspend fun calculateMd5Hash(image: ImageItem): String? {
        return md5HashCalculator.calculate(image.uri)
    }

    override suspend fun calculatePerceptualHash(image: ImageItem): String? {
        return perceptualHashCalculator.calculate(image.uri)
    }

    override suspend fun getCachedHash(imageId: Long): String? {
        return imageHashDao.getByImageId(imageId)?.md5Hash
    }

    override suspend fun saveHash(imageId: Long, md5Hash: String, perceptualHash: String?) {
        val image = mediaStoreDataSource.getImageById(imageId) ?: return

        val entity = ImageHashEntity(
            imageId = imageId,
            path = image.path,
            md5Hash = md5Hash,
            perceptualHash = perceptualHash,
            dateModified = image.dateModified,
            size = image.size
        )

        imageHashDao.insert(entity)
    }

    override suspend fun findExactDuplicates(images: List<ImageItem>): List<DuplicateGroup> =
        withContext(Dispatchers.Default) {
            val imagesBySize = images.groupBy { it.size }
                .filter { it.value.size >= 2 }

            val duplicateGroups = mutableListOf<DuplicateGroup>()

            imagesBySize.forEach { (_, sameSize) ->
                val imagesByHash = sameSize
                    .filter { it.md5Hash != null }
                    .groupBy { it.md5Hash!! }
                    .filter { it.value.size >= 2 }

                imagesByHash.forEach { (_, duplicates) ->
                    val sortedDuplicates = duplicates.sortedBy { it.dateModified }
                    val totalSize = duplicates.sumOf { it.size }
                    val savings = duplicates.drop(1).sumOf { it.size }

                    duplicateGroups.add(
                        DuplicateGroup(
                            id = UUID.randomUUID().toString(),
                            images = sortedDuplicates,
                            matchType = MatchType.EXACT,
                            similarityScore = 1.0f,
                            totalSize = totalSize,
                            potentialSavings = savings
                        )
                    )
                }
            }

            duplicateGroups
        }

    override suspend fun findSimilarImages(
        images: List<ImageItem>,
        threshold: Float
    ): List<DuplicateGroup> = withContext(Dispatchers.Default) {
        val imagesWithPHash = images.filter { it.perceptualHash != null }
        val processed = mutableSetOf<Long>()
        val duplicateGroups = mutableListOf<DuplicateGroup>()

        for (i in imagesWithPHash.indices) {
            val image1 = imagesWithPHash[i]
            if (image1.id in processed) continue

            val similarImages = mutableListOf(image1)

            for (j in (i + 1) until imagesWithPHash.size) {
                val image2 = imagesWithPHash[j]
                if (image2.id in processed) continue

                val similarity = PerceptualHashCalculator.similarity(
                    image1.perceptualHash!!,
                    image2.perceptualHash!!
                )

                if (similarity >= threshold) {
                    similarImages.add(image2)
                }
            }

            if (similarImages.size >= 2) {
                similarImages.forEach { processed.add(it.id) }

                val sortedImages = similarImages.sortedBy { it.dateModified }
                val avgSimilarity = if (similarImages.size > 1) {
                    var totalSim = 0f
                    var count = 0
                    for (x in similarImages.indices) {
                        for (y in (x + 1) until similarImages.size) {
                            totalSim += PerceptualHashCalculator.similarity(
                                similarImages[x].perceptualHash!!,
                                similarImages[y].perceptualHash!!
                            )
                            count++
                        }
                    }
                    if (count > 0) totalSim / count else 0f
                } else 0f

                duplicateGroups.add(
                    DuplicateGroup(
                        id = UUID.randomUUID().toString(),
                        images = sortedImages,
                        matchType = MatchType.SIMILAR,
                        similarityScore = avgSimilarity,
                        totalSize = sortedImages.sumOf { it.size },
                        potentialSavings = sortedImages.drop(1).sumOf { it.size }
                    )
                )
            }
        }

        duplicateGroups
    }

    override suspend fun applyFilter(
        groups: List<DuplicateGroup>,
        criteria: FilterCriteria
    ): List<DuplicateGroup> = withContext(Dispatchers.Default) {
        if (!criteria.hasActiveFilters) return@withContext groups

        groups.mapNotNull { group ->
            val filteredImages = group.images.filter { image ->
                val folderMatch = criteria.folders.isEmpty() ||
                        criteria.folders.any { image.path.contains(it) }

                val dateMatch = criteria.dateRange == null ||
                        (image.dateModified >= criteria.dateRange.startDate &&
                                image.dateModified <= criteria.dateRange.endDate)

                val sizeMatch = (criteria.minSize == null || image.size >= criteria.minSize) &&
                        (criteria.maxSize == null || image.size <= criteria.maxSize)

                val typeMatch = criteria.mimeTypes.isEmpty() ||
                        criteria.mimeTypes.any { image.mimeType.contains(it, true) }

                folderMatch && dateMatch && sizeMatch && typeMatch
            }

            if (filteredImages.size >= 2) {
                group.copy(
                    images = filteredImages,
                    totalSize = filteredImages.sumOf { it.size },
                    potentialSavings = filteredImages.drop(1).sumOf { it.size }
                )
            } else null
        }.filter { criteria.matchTypes.contains(it.matchType) }
    }

    override suspend fun deleteImages(images: List<ImageItem>): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                var deletedCount = 0

                images.forEach { image ->
                    try {
                        val result = contentResolver.delete(image.uri, null, null)
                        if (result > 0) {
                            imageHashDao.deleteByImageId(image.id)
                            deletedCount++
                        }
                    } catch (securityException: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val recoverableException =
                                securityException as? RecoverableSecurityException
                            recoverableException?.userAction?.actionIntent?.intentSender
                        }
                    }
                }

                Result.success(deletedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getFolders(): List<String> {
        return mediaStoreDataSource.getFolders()
    }

    override suspend fun getImageCount(): Int {
        return mediaStoreDataSource.getImageCount()
    }
}
