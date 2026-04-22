package com.duplicatefinder.data.repository

import android.content.res.AssetManager
import java.io.InputStream

interface OverlayCleaningBundledModelSource {
    val fileName: String
    val sourceId: String?
    fun exists(): Boolean
    fun open(): InputStream?
}

class AssetOverlayCleaningBundledModelSource(
    private val assetManager: AssetManager,
    private val assetPath: String
) : OverlayCleaningBundledModelSource {

    override val fileName: String = assetPath.substringAfterLast('/')
    override val sourceId: String = "asset://$assetPath"

    override fun exists(): Boolean {
        val parentPath = assetPath.substringBeforeLast('/', "")
        return runCatching {
            assetManager.list(parentPath)?.contains(fileName) == true
        }.getOrDefault(false)
    }

    override fun open(): InputStream? {
        return runCatching { assetManager.open(assetPath) }.getOrNull()
    }
}
