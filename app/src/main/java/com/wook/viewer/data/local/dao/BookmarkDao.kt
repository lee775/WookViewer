package com.wook.viewer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wook.viewer.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE uriString = :uriString ORDER BY pageIndex ASC")
    fun observeForDocument(uriString: String): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE uriString = :uriString AND pageIndex = :pageIndex)")
    suspend fun exists(uriString: String, pageIndex: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE uriString = :uriString AND pageIndex = :pageIndex")
    suspend fun remove(uriString: String, pageIndex: Int): Int

    @Query("DELETE FROM bookmarks WHERE uriString = :uriString")
    suspend fun removeAll(uriString: String): Int
}
