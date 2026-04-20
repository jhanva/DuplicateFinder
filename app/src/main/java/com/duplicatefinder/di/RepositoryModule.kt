package com.duplicatefinder.di

import com.duplicatefinder.data.repository.ImageRepositoryImpl
import com.duplicatefinder.data.repository.OverlayCleaningRepositoryImpl
import com.duplicatefinder.data.repository.OverlayModelBundleRepositoryImpl
import com.duplicatefinder.data.repository.OverlayRepositoryImpl
import com.duplicatefinder.data.repository.QualityRepositoryImpl
import com.duplicatefinder.data.repository.SettingsRepositoryImpl
import com.duplicatefinder.data.repository.TrashRepositoryImpl
import com.duplicatefinder.domain.repository.ImageRepository
import com.duplicatefinder.domain.repository.OverlayCleaningRepository
import com.duplicatefinder.domain.repository.OverlayModelBundleRepository
import com.duplicatefinder.domain.repository.OverlayRepository
import com.duplicatefinder.domain.repository.QualityRepository
import com.duplicatefinder.domain.repository.SettingsRepository
import com.duplicatefinder.domain.repository.TrashRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindImageRepository(
        impl: ImageRepositoryImpl
    ): ImageRepository

    @Binds
    @Singleton
    abstract fun bindTrashRepository(
        impl: TrashRepositoryImpl
    ): TrashRepository

    @Binds
    @Singleton
    abstract fun bindQualityRepository(
        impl: QualityRepositoryImpl
    ): QualityRepository

    @Binds
    @Singleton
    abstract fun bindOverlayRepository(
        impl: OverlayRepositoryImpl
    ): OverlayRepository

    @Binds
    @Singleton
    abstract fun bindOverlayModelBundleRepository(
        impl: OverlayModelBundleRepositoryImpl
    ): OverlayModelBundleRepository

    @Binds
    @Singleton
    abstract fun bindOverlayCleaningRepository(
        impl: OverlayCleaningRepositoryImpl
    ): OverlayCleaningRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
}
