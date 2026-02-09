package com.duplicatefinder.di

import android.content.Context
import com.duplicatefinder.data.local.datastore.SettingsDataStore
import com.duplicatefinder.data.media.MediaStoreDataSource
import com.duplicatefinder.util.hash.DifferenceHashCalculator
import com.duplicatefinder.util.hash.MD5HashCalculator
import com.duplicatefinder.util.hash.PerceptualHashCalculator
import com.duplicatefinder.util.image.ImageProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideMediaStoreDataSource(
        @ApplicationContext context: Context
    ): MediaStoreDataSource {
        return MediaStoreDataSource(context)
    }

    @Provides
    @Singleton
    fun provideMD5HashCalculator(
        @ApplicationContext context: Context
    ): MD5HashCalculator {
        return MD5HashCalculator(context)
    }

    @Provides
    @Singleton
    fun providePerceptualHashCalculator(
        @ApplicationContext context: Context
    ): PerceptualHashCalculator {
        return PerceptualHashCalculator(context)
    }

    @Provides
    @Singleton
    fun provideDifferenceHashCalculator(
        @ApplicationContext context: Context
    ): DifferenceHashCalculator {
        return DifferenceHashCalculator(context)
    }

    @Provides
    @Singleton
    fun provideImageProcessor(
        @ApplicationContext context: Context
    ): ImageProcessor {
        return ImageProcessor(context)
    }
}
