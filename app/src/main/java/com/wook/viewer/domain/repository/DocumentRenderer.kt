package com.wook.viewer.domain.repository

import android.net.Uri
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.PageSize
import com.wook.viewer.domain.model.RenderedPage
import com.wook.viewer.domain.model.SearchHit

/**
 * 포맷별 렌더 모듈이 구현하는 공통 인터페이스.
 *
 * 핸들 기반 설계: open()으로 받은 핸들이 닫히기 전까지 동일한 문서를 가리킨다.
 * 모든 메서드는 일시적이며 호출 후 백그라운드 스레드에서 실행되도록 설계되어야 한다.
 */
interface DocumentRenderer {

    /** 이 렌더러가 처리할 수 있는 포맷. */
    val supportedFormat: DocumentFormat

    /** 문서를 열고 핸들을 반환한다. 닫지 않으면 자원이 누수된다. */
    suspend fun open(uri: Uri): DocumentHandle

    /** 페이지(또는 슬라이드/화면) 수. */
    suspend fun pageCount(handle: DocumentHandle): Int

    /** 페이지의 원본 크기 (포인트 단위). 줌 계산에 사용. */
    suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize

    /**
     * 페이지 렌더링.
     * targetWidthPx에 맞춰 스케일된 비트맵을 반환.
     */
    suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage

    /** 텍스트 검색. 미지원 포맷은 빈 리스트 반환. */
    suspend fun search(handle: DocumentHandle, query: String): List<SearchHit> = emptyList()

    /** 핸들 닫기. */
    suspend fun close(handle: DocumentHandle)
}

/** 불투명 핸들 — 각 렌더러 구현이 내부 상태를 들고 있는다. */
interface DocumentHandle {
    val uri: Uri
}
