package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.repository.ImageRepository
import javax.inject.Inject

class DeleteImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository
) {
    suspend operator fun invoke(images: List<ImageItem>): Result<Int> {
        return imageRepository.deleteImages(images)
    }
}
