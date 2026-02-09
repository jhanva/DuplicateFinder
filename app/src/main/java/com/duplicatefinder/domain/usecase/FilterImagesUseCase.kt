package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.FilterCriteria
import com.duplicatefinder.domain.repository.ImageRepository
import javax.inject.Inject

class FilterImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository
) {
    suspend operator fun invoke(
        groups: List<DuplicateGroup>,
        criteria: FilterCriteria
    ): List<DuplicateGroup> {
        return imageRepository.applyFilter(groups, criteria)
    }
}
