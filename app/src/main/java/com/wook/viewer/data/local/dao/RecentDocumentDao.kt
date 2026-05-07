package com.wook.viewer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wook.viewer.data.local.entity.RecentDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocumentDao {

    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeAll(limit: Int): Flow<List<RecentDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentDocumentEntity)

    @Query("UPDATE recent_documents SET lastPageIndex = :pageIndex, lastOpenedAt = :openedAt WHERE uriString = :uriString")
    suspend fun updateLastPage(uriString: String, pageIndex: Int, openedAt: Long): Int

    @Query("DELETE FROM recent_documents WHERE uriString = :uriString")
    suspend fun delete(uriString: String): Int
}
