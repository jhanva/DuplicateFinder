package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.MatchType
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
    suspend operator fun invoke(images: List<ImageItem>): List<DuplicateGroup> =
        withContext(Dispatchers.Default) {
            val threshold = settingsRepository.similarityThreshold.first()

            val exactDuplicates = imageRepository.findExactDuplicates(images)
            val similarImages = imageRepository.findSimilarImages(images, threshold)

            mergeDuplicateGroups(exactDuplicates, similarImages)
        }

    private fun mergeDuplicateGroups(
        exact: List<DuplicateGroup>,
        similar: List<DuplicateGroup>
    ): List<DuplicateGroup> {
        val mergedGroups = mutableListOf<DuplicateGroup>()
        val processedIds = mutableSetOf<Long>()

        exact.forEach { group ->
            mergedGroups.add(group)
            group.images.forEach { processedIds.add(it.id) }
        }

        similar.forEach { group ->
            val newImages = group.images.filterNot { it.id in processedIds }
            if (newImages.size >= 2) {
                val existingGroup = mergedGroups.find { existing ->
                    existing.images.any { it.id in group.images.map { img -> img.id } }
                }

                if (existingGroup != null) {
                    val index = mergedGroups.indexOf(existingGroup)
                    val combinedImages = (existingGroup.images + newImages).distinctBy { it.id }
                    val updatedGroup = existingGroup.copy(
                        images = combinedImages,
                        matchType = MatchType.BOTH,
                        totalSize = combinedImages.sumOf { it.size },
                        potentialSavings = combinedImages.drop(1).sumOf { it.size }
                    )
                    mergedGroups[index] = updatedGroup
                } else {
                    mergedGroups.add(group)
                }
                newImages.forEach { processedIds.add(it.id) }
            }
        }

        return mergedGroups.sortedByDescending { it.potentialSavings }
    }
}
