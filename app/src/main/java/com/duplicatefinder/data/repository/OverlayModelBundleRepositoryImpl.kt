package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.repository.OverlayModelBundleInfo
import com.duplicatefinder.domain.repository.OverlayModelBundleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class OverlayModelBundleRepositoryImpl @Inject constructor(
    @Named("overlayModelManifestUrl") private val manifestUrl: String,
    @Named("overlayModelBundleDir") private val bundleDir: File
) : OverlayModelBundleRepository {

    override suspend fun getActiveBundleInfo(): OverlayModelBundleInfo? = withContext(Dispatchers.IO) {
        val manifestFile = File(bundleDir, MANIFEST_FILE_NAME)
        if (!manifestFile.exists()) return@withContext null

        runCatching {
            manifestFile.readText().toBundleInfo(manifestUrl)
        }.getOrNull()
    }

    override suspend fun ensureBundleAvailable(): OverlayModelBundleInfo? {
        return getActiveBundleInfo()
    }

    override suspend fun downloadBundle(): Result<OverlayModelBundleInfo> = withContext(Dispatchers.IO) {
        if (manifestUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Overlay model manifest URL is not configured.")
            )
        }

        runCatching {
            bundleDir.mkdirs()
            val manifestJson = downloadText(manifestUrl)
            val bundleInfo = manifestJson.toBundleInfo(manifestUrl)

            File(bundleDir, MANIFEST_FILE_NAME).writeText(manifestJson)
            downloadAsset(bundleInfo.detectorStage1Path)
            downloadAsset(bundleInfo.detectorStage2Path)
            downloadAsset(bundleInfo.inpainterPath)
            bundleInfo
        }
    }

    private fun downloadAsset(path: String) {
        val source = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            URL(URL(manifestUrl), path).toString()
        }
        val target = File(bundleDir, path.substringAfterLast('/'))
        URL(source).openConnection().let { connection ->
            connection as HttpURLConnection
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Failed to download overlay asset: $source")
            }
            target.outputStream().use { output ->
                connection.inputStream.use { input -> input.copyTo(output) }
            }
            connection.disconnect()
        }
    }

    private fun downloadText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.connect()
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Failed to download overlay manifest: $url")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun String.toBundleInfo(defaultManifestUrl: String): OverlayModelBundleInfo {
        val json = JSONObject(this)
        return OverlayModelBundleInfo(
            bundleVersion = json.optString("bundleVersion", "overlay-bundle-v1"),
            detectorStage1Path = json.getString("detectorStage1Path"),
            detectorStage2Path = json.getString("detectorStage2Path"),
            inpainterPath = json.getString("inpainterPath"),
            inputSizeStage1 = json.optInt("inputSizeStage1", 512),
            inputSizeStage2 = json.optInt("inputSizeStage2", 512),
            inputSizeInpainter = json.optInt("inputSizeInpainter", 1024),
            manifestUrl = defaultManifestUrl
        )
    }

    companion object {
        private const val MANIFEST_FILE_NAME = "bundle.json"
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
