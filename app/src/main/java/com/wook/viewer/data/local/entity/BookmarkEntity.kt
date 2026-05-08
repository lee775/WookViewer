package com.wook.viewer.data.local.entity

import androidx.room.Entity

@Entity(tableName = "bookmarks", primaryKeys = ["uriString", "pageIndex"])
data class BookmarkEntity(
    val uriString: String,
    val pageIndex: Int,
    val createdAt: Long,
    val label: String? = null
)
