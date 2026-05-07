package com.wook.viewer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_documents")
data class RecentDocumentEntity(
    @PrimaryKey val uriString: String,
    val displayName: String,
    val formatName: String,
    val lastOpenedAt: Long,
    val lastPageIndex: Int = 0
)
