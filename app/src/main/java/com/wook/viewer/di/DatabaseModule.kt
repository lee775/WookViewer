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
        Room.databaseBuilder(context, WookDatabase::class.java, WookDatabase.NAME)
            // v0.5 알파 — 마이그레이션 미정의. 사용자 데이터(최근 목록)는 destructively
            // 다시 만들 만한 영향이라 OK. 정식 출시 전 마이그레이션 정의 필요.
            .fallbackToDestructiveMigration(false)
            .build()

    @Provides
    fun provideRecentDocumentDao(db: WookDatabase): RecentDocumentDao = db.recentDocumentDao()

    @Provides
    fun provideBookmarkDao(db: WookDatabase): com.wook.viewer.data.local.dao.BookmarkDao =
        db.bookmarkDao()
}
