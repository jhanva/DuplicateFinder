package com.duplicatefinder.domain.repository

import com.duplicatefinder.domain.model.DuplicateGroup
import com.duplicatefinder.domain.model.ScanMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache of the most recent duplicate scan so screens can share the
 * result instead of re-running the full hashing pipeline. With 80k+ image
 * libraries a redundant re-scan is the single most expensive operation in the
 * app, so the scan result is computed once and consumed everywhere.
 */
@Singleton
class ScanResultStore @Inject constructor() {

    data class Snapshot(
        val groups: List<DuplicateGroup>,
        val folders: Set<String>,
        val scanMode: ScanMode,
        val timestampMillis: Long
    )

    @Volatile
    private var snapshot: Snapshot? = null

    fun save(groups: List<DuplicateGroup>, folders: Set<String>, scanMode: ScanMode) {
        snapshot = Snapshot(
            groups = groups,
            folders = folders,
            scanMode = scanMode,
            timestampMillis = System.currentTimeMillis()
        )
    }

    /** Returns the snapshot only if it matches the requested scan configuration. */
    fun get(folders: Set<String>, scanMode: ScanMode): Snapshot? {
        val current = snapshot ?: return null
        return current.takeIf { it.folders == folders && it.scanMode == scanMode }
    }

    /** Keeps the snapshot in sync after in-place mutations such as deletions. */
    fun updateGroups(groups: List<DuplicateGroup>) {
        val current = snapshot ?: return
        snapshot = current.copy(groups = groups)
    }

    fun clear() {
        snapshot = null
    }
}
