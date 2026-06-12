package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.MatchType
import com.duplicatefinder.domain.model.ScanMode
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FindDuplicatesUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        images: List<ImageItem>,
        scanMode: ScanMode
    ): List<DuplicateGroup> =
        withContext(Dispatchers.Default) {
            val exactDuplicates = imageRepository.findExactDuplicates(images)
            if (scanMode == ScanMode.EXACT) {
                return@withContext exactDuplicates.sortedByDescending { it.potentialSavings }
            }

            val threshold = settingsRepository.similarityThreshold.first()
            val similarImages = imageRepository.findSimilarImages(images, threshold)

            mergeDuplicateGroups(exactDuplicates, similarImages)
        }

    private fun mergeDuplicateGroups(
        exact: List<DuplicateGroup>,
        similar: List<DuplicateGroup>
    ): List<DuplicateGroup> {
        val mergedGroups = mutableListOf<DuplicateGroup>()
        val processedIds = mutableSetOf<Long>()
        // imageId -> index in mergedGroups, so each similar group resolves its
        // overlapping exact group in O(images) instead of scanning all groups.
        val groupIndexByImageId = HashMap<Long, Int>()

        exact.forEach { group ->
            val index = mergedGroups.size
            mergedGroups.add(group)
            group.images.forEach { image ->
                processedIds.add(image.id)
                groupIndexByImageId[image.id] = index
            }
        }

        similar.forEach { group ->
            val newImages = group.images.filterNot { it.id in processedIds }
            if (newImages.size >= 2) {
                val existingIndex = group.images.firstNotNullOfOrNull { image ->
                    groupIndexByImageId[image.id]
                }

                val targetIndex = if (existingIndex != null) {
                    val existingGroup = mergedGroups[existingIndex]
                    val combinedImages = (existingGroup.images + newImages).distinctBy { it.id }
                    mergedGroups[existingIndex] = existingGroup.copy(
                        images = combinedImages,
                        matchType = MatchType.BOTH,
                        totalSize = combinedImages.sumOf { it.size },
                        potentialSavings = combinedImages.drop(1).sumOf { it.size }
                    )
                    existingIndex
                } else {
                    mergedGroups.add(group)
                    mergedGroups.size - 1
                }

                newImages.forEach { image ->
                    processedIds.add(image.id)
                    groupIndexByImageId[image.id] = targetIndex
                }
            }
        }

        return mergedGroups.sortedByDescending { it.potentialSavings }
    }
}
