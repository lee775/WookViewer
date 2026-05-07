package com.wook.viewer.domain.model

data class RecentDocument(
    val uriString: String,
    val displayName: String,
    val format: DocumentFormat,
    val lastOpenedAt: Long,
    val lastPageIndex: Int = 0
)
