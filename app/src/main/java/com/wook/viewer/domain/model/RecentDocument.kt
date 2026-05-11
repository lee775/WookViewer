package com.wook.viewer.domain.model

data class RecentDocument(
    val uriString: String,
    val displayName: String,
    val format: DocumentFormat,
    val lastOpenedAt: Long,
    val lastPageIndex: Int = 0,
    val pinned: Boolean = false
)

/** 최근 문서 목록 정렬 옵션. 핀 항목은 항상 맨 위에 고정된다. */
enum class RecentSortOrder { RECENT, NAME }
