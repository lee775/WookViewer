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

    /**
     * 비밀번호와 함께 문서를 연다 (암호 보호 문서용).
     *
     * 기본 구현은 [open] 으로 위임 — 비밀번호를 무시한다.
     * 비밀번호 해제를 지원하는 렌더러만 오버라이드 (현재 PDF만 지원).
     *
     * 잘못된 비밀번호면 [com.wook.viewer.domain.error.DocumentError.PasswordProtected]
     * (wrongPassword=true)를 던진다.
     */
    suspend fun open(uri: Uri, password: String): DocumentHandle = open(uri)

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
     * 위치 정보가 없는 단순 표시용 — 자세한 위치는 [getPageElements] 사용.
     */
    suspend fun getPageImages(handle: DocumentHandle, index: Int): List<android.graphics.Bitmap> = emptyList()

    /**
     * 페이지를 텍스트/이미지 요소의 순서가 있는 리스트로 제공 (DOCX 인라인 그림 등).
     *
     * null 반환 시 UI는 [getPageText] + [getPageImages] 폴백을 사용한다.
     * 위치를 보존하고 싶은 렌더러만 오버라이드.
     */
    suspend fun getPageElements(
        handle: DocumentHandle,
        index: Int
    ): List<com.wook.viewer.domain.model.PageElement>? = null

    /**
     * 문서가 명시적 "섹션 선택" UI를 제공해야 하는 경우 섹션 이름 리스트.
     *
     * - XLSX: 시트 이름들 (사용자가 시트 탭으로 선택)
     * - 그 외: null (일반 페이지 모델 사용)
     *
     * 반환 길이는 [pageCount] 와 동일해야 한다 (각 페이지가 하나의 섹션).
     */
    suspend fun getSectionLabels(handle: DocumentHandle): List<String>? = null

    /** 핸들 닫기. */
    suspend fun close(handle: DocumentHandle)
}

/** 불투명 핸들 — 각 렌더러 구현이 내부 상태를 들고 있는다. */
interface DocumentHandle {
    val uri: Uri
}
