package com.duplicatefinder.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Installs the overlay detection model that ships inside the APK
 * (`assets/overlay_models/`) into the app's private storage
 * ([bundleDir]) where the ONNX runtime loads it from.
 *
 * This keeps the app fully offline for the end user: the model travels in
 * the APK and is copied locally on first use — nothing is ever downloaded
 * and the user never places files by hand. If a future APK ships a new model
 * version, the bundled copy is reinstalled automatically.
 */
interface OverlayModelAssetInstaller {
    suspend fun installIfNeeded()
}

@Singleton
class OverlayModelAssetInstallerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("overlayModelBundleDir") private val bundleDir: File
) : OverlayModelAssetInstaller {

    @Volatile
    private var verifiedVersion: String? = null

    override suspend fun installIfNeeded() = withContext(Dispatchers.IO) {
        val assetManifest = runCatching {
            context.assets.open("$ASSET_DIR/$MANIFEST_FILE_NAME").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext // no model bundled in this build

        val assetVersion = runCatching { JSONObject(assetManifest).optString("bundleVersion") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext

        if (assetVersion == verifiedVersion) return@withContext

        val modelFiles = modelFileNames(assetManifest)
        if (isInstalled(assetVersion, modelFiles)) {
            verifiedVersion = assetVersion
            return@withContext
        }

        bundleDir.mkdirs()
        (listOf(MANIFEST_FILE_NAME) + modelFiles).forEach { name ->
            context.assets.open("$ASSET_DIR/$name").use { input ->
                File(bundleDir, name).outputStream().use { output -> input.copyTo(output) }
            }
        }
        verifiedVersion = assetVersion
    }

    private fun isInstalled(assetVersion: String, modelFiles: List<String>): Boolean {
        val manifest = File(bundleDir, MANIFEST_FILE_NAME)
        if (!manifest.exists()) return false
        val installedVersion = runCatching { JSONObject(manifest.readText()).optString("bundleVersion") }
            .getOrNull()
        if (installedVersion != assetVersion) return false
        return modelFiles.all { name ->
            File(bundleDir, name).let { it.exists() && it.length() > 0L }
        }
    }

    private fun modelFileNames(manifest: String): List<String> {
        val json = JSONObject(manifest)
        return listOf(
            "textDetectorPath",
            "maskRefinerEncoderPath",
            "maskRefinerDecoderPath"
        ).mapNotNull { key ->
            json.optString(key).takeIf { it.isNotBlank() }?.substringAfterLast('/')
        }.distinct()
    }

    companion object {
        private const val ASSET_DIR = "overlay_models"
        private const val MANIFEST_FILE_NAME = "bundle.json"
    }
}
