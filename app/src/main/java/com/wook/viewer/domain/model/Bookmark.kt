package com.wook.viewer.domain.model

data class Bookmark(
    val uriString: String,
    val pageIndex: Int,
    val createdAt: Long,
    val label: String? = null
)
