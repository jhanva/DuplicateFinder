package com.duplicatefinder.domain.usecase

import com.duplicatefinder.domain.model.ImageItem
import com.duplicatefinder.domain.model.ScanPhase
import com.duplicatefinder.domain.model.ScanProgress
import com.duplicatefinder.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ScanImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository
) {
    operator fun invoke(): Flow<Pair<ScanProgress, List<ImageItem>>> = flow {
        emit(ScanProgress(ScanPhase.LOADING, 0, 0) to emptyList())

        val images = imageRepository.getAllImages()
        val total = images.size

        emit(ScanProgress(ScanPhase.HASHING, 0, total) to emptyList())

        val hashedImages = mutableListOf<ImageItem>()

        images.forEachIndexed { index, image ->
            val cachedHash = imageRepository.getCachedHash(image.id)

            val hashedImage = if (cachedHash != null) {
                image.copy(md5Hash = cachedHash)
            } else {
                val md5 = imageRepository.calculateMd5Hash(image)
                val pHash = imageRepository.calculatePerceptualHash(image)

                if (md5 != null) {
                    imageRepository.saveHash(image.id, md5, pHash)
                }

                image.copy(md5Hash = md5, perceptualHash = pHash)
            }

            hashedImages.add(hashedImage)

            emit(
                ScanProgress(
                    phase = ScanPhase.HASHING,
                    current = index + 1,
                    total = total,
                    currentFile = image.name
                ) to hashedImages.toList()
            )
        }

        emit(ScanProgress(ScanPhase.COMPLETE, total, total) to hashedImages)
    }.flowOn(Dispatchers.Default)
}
