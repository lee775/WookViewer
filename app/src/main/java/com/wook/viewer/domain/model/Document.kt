package com.wook.viewer.domain.model

import android.net.Uri

data class Document(
    val uri: Uri,
    val displayName: String,
    val format: DocumentFormat,
    val sizeBytes: Long?,
    val lastModified: Long?
)

data class RenderedPage(
    val index: Int,
    val widthPx: Int,
    val heightPx: Int,
    val bitmap: android.graphics.Bitmap
)

data class PageSize(val widthPt: Float, val heightPt: Float)

data class SearchHit(
    val pageIndex: Int,
    val snippet: String,
    val rangeStart: Int,
    val rangeEnd: Int
)
