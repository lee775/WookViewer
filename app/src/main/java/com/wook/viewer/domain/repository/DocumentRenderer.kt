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

    /** 이 렌더러가 처리할 수 있는 포맷(들). 한 렌더러가 여러 포맷을 다룰 수 있다. */
    val supportedFormats: Set<DocumentFormat>

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

    /**
     * 페이지의 평문 텍스트.
     *
     * - TEXT_ONLY 포맷(HWP/DOCX/PPTX): 해당 페이지 텍스트 반환 → UI가 SelectionContainer로
     *   감싸 텍스트 선택/복사 가능하게 표시
     * - FULL fidelity 포맷(PDF): 기본 구현이 null 반환 → UI는 비트맵 렌더 사용
     */
    suspend fun getPageText(handle: DocumentHandle, index: Int): String? = null

    /**
     * 페이지에 포함된 임베디드 이미지 (TEXT_ONLY 포맷용).
     *
     * Office/HWP 같은 ZIP 기반 포맷은 본문 텍스트만으로는 시각 정보가 빠지므로,
     * 문서 안의 그림 파일을 꺼내서 UI에 함께 표시한다.
     *
     * - PPTX: 슬라이드별로 해당 슬라이드에 참조된 이미지만 반환
     * - DOCX/XLSX/HWP: 위치 정보가 어려워 첫 페이지(index == 0)에 모두 표시
     * - PDF/IMAGE: 기본 빈 리스트 (이미 비트맵 자체가 시각 그대로)
     */
    suspend fun getPageImages(handle: DocumentHandle, index: Int): List<android.graphics.Bitmap> = emptyList()

    /** 핸들 닫기. */
    suspend fun close(handle: DocumentHandle)
}

/** 불투명 핸들 — 각 렌더러 구현이 내부 상태를 들고 있는다. */
interface DocumentHandle {
    val uri: Uri
}
