package com.wook.viewer.render.lok

import android.net.Uri
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.PageSize
import com.wook.viewer.domain.model.RenderedPage
import com.wook.viewer.domain.repository.DocumentHandle
import com.wook.viewer.domain.repository.DocumentRenderer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibreOfficeKit 기반 렌더러 — Office 파일 100% 재현 목표.
 *
 * **현재 상태 (Session 1)**: 스캐폴딩만. 네이티브 라이브러리 미빌드.
 * [com.wook.viewer.data.lok.LokAvailability] 가 항상 false를 반환하므로
 * [com.wook.viewer.render.RendererRegistryImpl] 의 라우터가 이 렌더러를
 * 호출하지 않는다. open() 등이 직접 호출되면 즉시 예외.
 *
 * **로드맵**:
 *   - S2: native lib 빌드 (.github/workflows/build-libreoffice-android.yml)
 *   - S3: JNI 바인딩 + 단일 페이지 비트맵 렌더
 *   - S4: 다중 페이지 + 텍스트 검색 + 캐싱
 *   - S5: APK 슬림화 (ABI split), 마무리
 */
@Singleton
class LokDocumentRenderer @Inject constructor() : DocumentRenderer {

    /**
     * 일부러 빈 set: Hilt @IntoSet 으로 등록되어도
     * 기존 [DocumentFormat] → renderer 매핑을 덮어쓰지 않도록.
     * 실제 라우팅은 [com.wook.viewer.render.RendererRegistryImpl] 가
     * [LOK_SUPPORTED_FORMATS] 를 참조해 별도로 처리.
     */
    override val supportedFormats: Set<DocumentFormat> = emptySet()

    override suspend fun open(uri: Uri): DocumentHandle =
        notImplemented()

    override suspend fun pageCount(handle: DocumentHandle): Int =
        notImplemented()

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize =
        notImplemented()

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = notImplemented()

    override suspend fun close(handle: DocumentHandle) {
        // no-op until JNI 바인딩 (Session 3)
    }

    private fun notImplemented(): Nothing =
        throw UnsupportedOperationException(
            "LibreOfficeKit 렌더링은 아직 구현되지 않았습니다 (S3 예정). " +
                "RendererRegistry가 라우팅하지 않아야 정상입니다."
        )
}

/** LOK 라우팅 대상 포맷 — RendererRegistryImpl 이 활성화 조건과 함께 사용. */
val LOK_SUPPORTED_FORMATS: Set<DocumentFormat> = setOf(
    DocumentFormat.DOCX,
    DocumentFormat.PPTX,
    DocumentFormat.XLSX,
    DocumentFormat.HWP
)
