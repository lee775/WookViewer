package com.wook.viewer.di

import com.wook.viewer.data.repository.AppSettingsRepositoryImpl
import com.wook.viewer.data.repository.DocumentRepositoryImpl
import com.wook.viewer.domain.repository.AppSettingsRepository
import com.wook.viewer.domain.repository.DocumentRepository
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
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: AppSettingsRepositoryImpl): AppSettingsRepository
}
