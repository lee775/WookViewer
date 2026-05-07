package com.wook.viewer.di

import android.content.Context
import androidx.room.Room
import com.wook.viewer.data.local.WookDatabase
import com.wook.viewer.data.local.dao.RecentDocumentDao
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
    fun provideDatabase(@ApplicationContext context: Context): WookDatabase =
        Room.databaseBuilder(context, WookDatabase::class.java, WookDatabase.NAME).build()

    @Provides
    fun provideRecentDocumentDao(db: WookDatabase): RecentDocumentDao = db.recentDocumentDao()
}
