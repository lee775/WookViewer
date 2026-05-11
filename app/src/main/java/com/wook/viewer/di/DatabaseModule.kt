package com.wook.viewer.di

import android.content.Context
import androidx.room.Room
import com.wook.viewer.data.local.MIGRATION_2_3
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
        Room.databaseBuilder(context, WookDatabase::class.java, WookDatabase.NAME)
            .addMigrations(MIGRATION_2_3)
            // 미래에 정의 안 된 버전 점프가 나면 데이터 소실 허용 (사용자 데이터는 최근 목록 + 북마크)
            .fallbackToDestructiveMigration(false)
            .build()

    @Provides
    fun provideRecentDocumentDao(db: WookDatabase): RecentDocumentDao = db.recentDocumentDao()

    @Provides
    fun provideBookmarkDao(db: WookDatabase): com.wook.viewer.data.local.dao.BookmarkDao =
        db.bookmarkDao()
}
