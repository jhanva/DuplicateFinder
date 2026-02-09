package com.duplicatefinder.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.duplicatefinder.data.local.db.dao.ImageHashDao
import com.duplicatefinder.data.local.db.dao.TrashDao
import com.duplicatefinder.data.local.db.entities.ImageHashEntity
import com.duplicatefinder.data.local.db.entities.TrashEntity

@Database(
    entities = [
        ImageHashEntity::class,
        TrashEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageHashDao(): ImageHashDao
    abstract fun trashDao(): TrashDao

    companion object {
        const val DATABASE_NAME = "duplicate_finder_db"
    }
}
