package com.wook.viewer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wook.viewer.data.local.dao.RecentDocumentDao
import com.wook.viewer.data.local.entity.RecentDocumentEntity

@Database(
    entities = [RecentDocumentEntity::class],
    version = 1,
    exportSchema = true
)
abstract class WookDatabase : RoomDatabase() {
    abstract fun recentDocumentDao(): RecentDocumentDao

    companion object {
        const val NAME = "wook_viewer.db"
    }
}
