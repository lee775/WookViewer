package com.wook.viewer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wook.viewer.data.local.entity.RecentDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocumentDao {

    /** 핀 항목이 항상 위. 그 다음 최근 열어본 순. */
    @Query("""
        SELECT * FROM recent_documents
        ORDER BY pinned DESC, lastOpenedAt DESC
        LIMIT :limit
    """)
    fun observeAllByRecent(limit: Int): Flow<List<RecentDocumentEntity>>

    /** 핀 항목이 항상 위. 그 다음 이름 오름차순 (대소문자 무시). */
    @Query("""
        SELECT * FROM recent_documents
        ORDER BY pinned DESC, displayName COLLATE NOCASE ASC
        LIMIT :limit
    """)
    fun observeAllByName(limit: Int): Flow<List<RecentDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentDocumentEntity)

    @Query("UPDATE recent_documents SET lastPageIndex = :pageIndex, lastOpenedAt = :openedAt WHERE uriString = :uriString")
    suspend fun updateLastPage(uriString: String, pageIndex: Int, openedAt: Long): Int

    @Query("UPDATE recent_documents SET pinned = :pinned WHERE uriString = :uriString")
    suspend fun setPinned(uriString: String, pinned: Boolean): Int

    /** 현재 핀 상태만 가볍게 조회. 없으면 null. */
    @Query("SELECT pinned FROM recent_documents WHERE uriString = :uriString")
    suspend fun getPinned(uriString: String): Boolean?

    @Query("DELETE FROM recent_documents WHERE uriString = :uriString")
    suspend fun delete(uriString: String): Int
}
