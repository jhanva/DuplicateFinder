package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.repository.TrashRepository
import javax.inject.Inject

class EmptyTrashUseCase @Inject constructor(
    private val trashRepository: TrashRepository
) {
    suspend operator fun invoke(): Result<Int> {
        return trashRepository.emptyTrash()
    }
}
