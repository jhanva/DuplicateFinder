package com.duplicatefinder.domain.repository

interface OverlayCleaningModelRepository {
    fun isDownloadConfigured(): Boolean
    suspend fun getActiveModelInfo(): OverlayModelBundleInfo?
    suspend fun downloadModel(): Result<OverlayModelBundleInfo>
}
