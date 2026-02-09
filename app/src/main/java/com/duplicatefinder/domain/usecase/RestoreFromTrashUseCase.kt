package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.TrashItem
import com.duplicatefinder.domain.repository.TrashRepository
import javax.inject.Inject

class RestoreFromTrashUseCase @Inject constructor(
    private val trashRepository: TrashRepository
) {
    suspend operator fun invoke(items: List<TrashItem>): Result<Int> {
        return trashRepository.restoreFromTrash(items)
    }
}
