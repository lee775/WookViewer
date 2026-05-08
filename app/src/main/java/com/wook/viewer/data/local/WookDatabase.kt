package com.wook.viewer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wook.viewer.data.local.dao.BookmarkDao
import com.wook.viewer.data.local.dao.RecentDocumentDao
import com.wook.viewer.data.local.entity.BookmarkEntity
import com.wook.viewer.data.local.entity.RecentDocumentEntity

@Database(
    entities = [RecentDocumentEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = true
)
abstract class WookDatabase : RoomDatabase() {
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val NAME = "wook_viewer.db"
    }
}
