package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.TrashItem
import com.duplicatefinder.domain.repository.TrashRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTrashItemsUseCase @Inject constructor(
    private val trashRepository: TrashRepository
) {
    operator fun invoke(): Flow<List<TrashItem>> {
        return trashRepository.getTrashItems()
    }
}
