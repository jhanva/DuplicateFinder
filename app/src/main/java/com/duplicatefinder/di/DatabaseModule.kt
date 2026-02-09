package com.duplicatefinder.di

import android.content.Context
import androidx.room.Room
import com.duplicatefinder.data.local.db.AppDatabase
import com.duplicatefinder.data.local.db.dao.ImageHashDao
import com.duplicatefinder.data.local.db.dao.TrashDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideImageHashDao(database: AppDatabase): ImageHashDao {
        return database.imageHashDao()
    }

    @Provides
    @Singleton
    fun provideTrashDao(database: AppDatabase): TrashDao {
        return database.trashDao()
    }
}
