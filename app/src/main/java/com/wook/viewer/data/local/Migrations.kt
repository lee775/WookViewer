package com.wook.viewer.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3 : RecentDocumentEntity.pinned 컬럼 추가.
 * 핀 고정 기능을 위한 Boolean(INTEGER 0/1) 컬럼, 기존 행은 false 로 초기화.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recent_documents ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }
}
