package com.duplicatefinder.di

import android.content.Context
import com.duplicatefinder.data.repository.AssetOverlayCleaningBundledModelSource
import com.duplicatefinder.data.repository.OverlayCleaningBundledModelSource
import com.duplicatefinder.BuildConfig
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
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private const val OVERLAY_CLEANING_BUNDLED_ASSET_PATH = "overlay-cleaning/AOT-GAN.onnx"

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

    @Provides
    @Singleton
    @Named("overlayModelManifestUrl")
    fun provideOverlayModelManifestUrl(): String = BuildConfig.OVERLAY_MODEL_MANIFEST_URL

    @Provides
    @Singleton
    @Named("overlayModelBundleDir")
    fun provideOverlayModelBundleDir(
        @ApplicationContext context: Context
    ): File = File(context.filesDir, "overlay_models/current").also { it.mkdirs() }

    @Provides
    @Singleton
    @Named("overlayCleaningModelUrl")
    fun provideOverlayCleaningModelUrl(): String = BuildConfig.OVERLAY_CLEANING_MODEL_URL

    @Provides
    @Singleton
    @Named("overlayCleaningModelDir")
    fun provideOverlayCleaningModelDir(
        @ApplicationContext context: Context
    ): File = File(context.filesDir, "overlay_models/current").also { it.mkdirs() }

    @Provides
    @Singleton
    fun provideOverlayCleaningBundledModelSource(
        @ApplicationContext context: Context
    ): OverlayCleaningBundledModelSource {
        return AssetOverlayCleaningBundledModelSource(
            assetManager = context.assets,
            assetPath = OVERLAY_CLEANING_BUNDLED_ASSET_PATH
        )
    }

    @Provides
    @Singleton
    @Named("overlayPreviewDir")
    fun provideOverlayPreviewDir(
        @ApplicationContext context: Context
    ): File = File(context.cacheDir, "overlay_preview").also { it.mkdirs() }
}
