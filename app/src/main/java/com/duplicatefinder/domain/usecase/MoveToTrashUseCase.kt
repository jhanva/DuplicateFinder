package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.repository.TrashRepository
import javax.inject.Inject

class MoveToTrashUseCase @Inject constructor(
    private val trashRepository: TrashRepository
) {
    suspend operator fun invoke(images: List<ImageItem>): Result<Int> {
        return trashRepository.moveToTrash(images)
    }
}
