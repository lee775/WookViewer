package com.wook.viewer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Application 생애주기에 묶인 코루틴 스코프.
 * ViewModel scope가 죽은 후에도 자원 정리(파일 핸들 close 등)를 마쳐야 할 때 사용.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
