package com.wook.viewer.domain.repository

import android.net.Uri
import com.wook.viewer.domain.model.Bookmark
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.model.RecentDocument
import com.wook.viewer.domain.model.RecentSortOrder
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    /** SAF URI로부터 문서 메타데이터를 읽어 Document 모델로 만든다. */
    suspend fun resolveDocument(uri: Uri): Document?

    /** 최근 문서 목록. 핀 항목은 항상 맨 위, 그 다음 [order] 기준 정렬. */
    fun observeRecent(
        order: RecentSortOrder = RecentSortOrder.RECENT,
        limit: Int = 50
    ): Flow<List<RecentDocument>>

    suspend fun addOrUpdateRecent(doc: Document, lastPageIndex: Int = 0)

    suspend fun updateLastPage(uriString: String, pageIndex: Int)

    suspend fun setPinned(uriString: String, pinned: Boolean)

    suspend fun removeRecent(uriString: String)

    /** 텍스트 내용을 기존 URI 에 덮어쓰기. SAF write 권한 필요. */
    suspend fun writeTextContent(uri: Uri, content: String, charset: String = "UTF-8")

    // ---- Bookmarks ----

    fun observeBookmarks(uriString: String): Flow<List<Bookmark>>
    suspend fun isBookmarked(uriString: String, pageIndex: Int): Boolean
    suspend fun addBookmark(uriString: String, pageIndex: Int)
    suspend fun removeBookmark(uriString: String, pageIndex: Int)
    suspend fun toggleBookmark(uriString: String, pageIndex: Int): Boolean
}
