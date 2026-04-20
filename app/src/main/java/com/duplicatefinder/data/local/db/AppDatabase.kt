package com.duplicatefinder.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duplicatefinder.data.local.db.dao.ImageHashDao
import com.duplicatefinder.data.local.db.dao.ImageQualityDao
import com.duplicatefinder.data.local.db.dao.OverlayDetectionDao
import com.duplicatefinder.data.local.db.dao.TrashDao
import com.duplicatefinder.data.local.db.entities.ImageHashEntity
import com.duplicatefinder.data.local.db.entities.ImageQualityEntity
import com.duplicatefinder.data.local.db.entities.OverlayDetectionEntity
import com.duplicatefinder.data.local.db.entities.TrashEntity

@Database(
    entities = [
        ImageHashEntity::class,
        ImageQualityEntity::class,
        OverlayDetectionEntity::class,
        TrashEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageHashDao(): ImageHashDao
    abstract fun imageQualityDao(): ImageQualityDao
    abstract fun overlayDetectionDao(): OverlayDetectionDao
    abstract fun trashDao(): TrashDao

    companion object {
        const val DATABASE_NAME = "duplicate_finder_db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS image_quality_scores (
                        imageId INTEGER NOT NULL PRIMARY KEY,
                        path TEXT NOT NULL,
                        qualityScore REAL NOT NULL,
                        sharpness REAL NOT NULL,
                        detailDensity REAL NOT NULL,
                        blockiness REAL NOT NULL,
                        dateModified INTEGER NOT NULL,
                        size INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS overlay_detections (
                        imageId INTEGER NOT NULL PRIMARY KEY,
                        path TEXT NOT NULL,
                        preliminaryScore REAL NOT NULL,
                        refinedScore REAL NOT NULL,
                        overlayCoverageRatio REAL NOT NULL,
                        maskConfidence REAL NOT NULL,
                        overlayKinds TEXT NOT NULL,
                        regionsJson TEXT NOT NULL,
                        dateModified INTEGER NOT NULL,
                        size INTEGER NOT NULL,
                        modelVersion TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
