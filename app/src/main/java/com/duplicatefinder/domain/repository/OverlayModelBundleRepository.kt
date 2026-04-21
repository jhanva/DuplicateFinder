package com.duplicatefinder.domain.repository

data class OverlayModelBundleInfo(
    val bundleVersion: String,
    val detectorStage1Path: String,
    val detectorStage2Path: String,
    val inpainterPath: String,
    val inputSizeStage1: Int,
    val inputSizeStage2: Int,
    val inputSizeInpainter: Int,
    val manifestUrl: String? = null
)

interface OverlayModelBundleRepository {
    fun isDownloadConfigured(): Boolean
    suspend fun getActiveBundleInfo(): OverlayModelBundleInfo?
    suspend fun ensureBundleAvailable(): OverlayModelBundleInfo?
    suspend fun downloadBundle(): Result<OverlayModelBundleInfo>
}
