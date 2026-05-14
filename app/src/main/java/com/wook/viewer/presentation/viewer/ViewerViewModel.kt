package com.wook.viewer.presentation.viewer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wook.viewer.di.ApplicationScope
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.Bookmark
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.RenderingFidelity
import com.wook.viewer.domain.repository.DocumentHandle
import com.wook.viewer.domain.repository.DocumentRenderer
import com.wook.viewer.domain.repository.DocumentRepository
import com.wook.viewer.domain.repository.RendererRegistry
import com.wook.viewer.render.lok.LOK_SUPPORTED_FORMATS
import com.wook.viewer.render.lok.LokDocumentRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** 페이지 내에서의 검색 매치 — 페이지 인덱스 + 텍스트 내 [start, end) 범위. */
data class SearchMatch(
    val pageIndex: Int,
    val rangeStart: Int,
    val rangeEnd: Int
)

/** 저장 결과 이벤트. UI 가 Snackbar/Toast 로 표시 후 [ViewerViewModel.clearSaveResult]. */
sealed interface SaveResult {
    data class Success(val message: String) : SaveResult
    data class Failure(val message: String) : SaveResult
}

/** PDF/이미지 뷰 모드. TEXT 모드는 PDF에만 의미가 있음 (이미지는 텍스트 없음). */
enum class PdfViewMode { BITMAP, TEXT }

data class ViewerUiState(
    val loading: Boolean = false,
    val document: Document? = null,
    val pageCount: Int = 0,
    val currentIndex: Int = 0,
    val error: DocumentError? = null,

    // PDF 보기 모드
    val pdfViewMode: PdfViewMode = PdfViewMode.BITMAP,

    // 섹션(XLSX 시트 등) — null이면 일반 페이지 모델
    val sectionLabels: List<String>? = null,

    /**
     * 비트맵 렌더링 강제. true 면 ViewerScreen 이 BitmapPageInline 사용 (LOK 활성 시).
     * false 면 포맷 기본 fidelity 따름 (TEXT_ONLY 는 TextPageInline).
     */
    val useBitmapRendering: Boolean = false,

    /** 편집 모드 — TXT/MD 에서만 의미. true 면 BasicTextField 활성. */
    val editMode: Boolean = false,
    /** 편집 중인 텍스트 (편집 모드 진입 시 첫 페이지 텍스트로 초기화). */
    val editedText: String = "",
    /**
     * Office 편집 모드 — DOCX/PPTX/XLSX 에서 LOK 기반 직접 편집.
     * UI 가 [OfficeEditOverlay] 를 활성화하고 키/탭 이벤트를 VM 으로 전달.
     */
    val officeEditMode: Boolean = false,
    /** Office 편집 시 tile 무효화 카운터 — 증가 시 BitmapItem 강제 재렌더. */
    val invalidationTick: Long = 0L,
    /** LOK 가 알려준 현재 커서 위치 (정규화 비율). null = 없음/숨김. */
    val officeCursor: LokDocumentRenderer.CursorState? = null,
    /** LOK 가 알려준 현재 텍스트 선택 영역들. */
    val officeSelection: List<LokDocumentRenderer.SelectionRect> = emptyList(),
    /** 문서에서 사용 가능한 글꼴 목록 — Office 편집 진입 시 로드. */
    val officeFonts: List<String> = emptyList(),
    /** 저장 진행 상태. */
    val saving: Boolean = false,
    /** 저장 결과 메시지 (Snackbar 등). */
    val saveResult: SaveResult? = null,

    // 문서 내부 목차 (PDF Outline 등) — null/빈 리스트면 아이콘 미노출
    val outline: List<com.wook.viewer.domain.model.OutlineNode>? = null,

    // 검색
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val searching: Boolean = false,

    // 북마크
    val bookmarks: List<Bookmark> = emptyList(),
    val currentPageBookmarked: Boolean = false
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repo: DocumentRepository,
    private val registry: RendererRegistry,
    @ApplicationScope private val appScope: CoroutineScope,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerUiState())
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private var renderer: DocumentRenderer? = null
    private var handle: DocumentHandle? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var bookmarkJob: Job? = null

    /** 페이지별 텍스트 캐시 — 검색에 사용. */
    private val pageTextCache = mutableMapOf<Int, String>()

    /** 검색 가능 여부 — TEXT_ONLY 포맷 OR PDF (PdfBox 텍스트 추출). 이미지는 불가. */
    val isSearchSupported: Boolean
        get() {
            val fmt = _state.value.document?.format ?: return false
            return fmt.fidelity == RenderingFidelity.TEXT_ONLY ||
                fmt == com.wook.viewer.domain.model.DocumentFormat.PDF
        }

    fun togglePdfViewMode() {
        val s = _state.value
        if (s.document?.format != com.wook.viewer.domain.model.DocumentFormat.PDF) return
        _state.update {
            it.copy(
                pdfViewMode = if (it.pdfViewMode == PdfViewMode.BITMAP) PdfViewMode.TEXT
                else PdfViewMode.BITMAP
            )
        }
    }

    fun load(uri: Uri) {
        if (_state.value.document?.uri == uri && _state.value.error == null) return
        startLoad(uri, password = null)
    }

    /** 비밀번호 다이얼로그에서 호출. 현재 문서를 비밀번호와 함께 다시 연다. */
    fun unlockWithPassword(password: String) {
        val uri = _state.value.document?.uri
            ?: return  // 문서 정보조차 없는 케이스 — 비정상
        if (password.isEmpty()) return
        startLoad(uri, password = password)
    }

    private fun startLoad(uri: Uri, password: String?) {
        loadJob?.cancel()
        searchJob?.cancel()
        bookmarkJob?.cancel()
        closeCurrentHandle()
        pageTextCache.clear()

        // 비밀번호 재시도는 document 정보를 유지해야 다이얼로그가 계속 노출됨
        val preservedDoc = _state.value.document.takeIf { password != null }
        _state.update {
            ViewerUiState(
                loading = true,
                document = preservedDoc
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val doc = preservedDoc ?: repo.resolveDocument(uri)
                if (doc == null) {
                    _state.update { it.copy(loading = false, error = DocumentError.IoError()) }
                    return@launch
                }
                // document를 먼저 설정 — open() 중 PasswordProtected 에러가 나도
                // unlockWithPassword가 URI를 알 수 있도록
                _state.update { it.copy(document = doc) }

                val r = registry.rendererFor(doc.format)
                if (r == null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = DocumentError.UnsupportedVariant(doc.format.displayName)
                        )
                    }
                    return@launch
                }

                val h = if (password != null) r.open(uri, password) else r.open(uri)
                renderer = r
                handle = h
                val count = r.pageCount(h)
                val labels = r.getSectionLabels(h)
                val outline = r.getOutline(h)
                // LOK 렌더러는 모든 포맷을 비트맵으로 그림 — ViewerScreen 분기 강제
                val useBitmap = r is LokDocumentRenderer
                _state.update {
                    it.copy(
                        loading = false,
                        useBitmapRendering = useBitmap,
                        pageCount = count,
                        currentIndex = 0,
                        sectionLabels = labels,
                        outline = outline
                    )
                }
                // LOK 일 때 tile invalidation + 커서 위치 구독
                if (r is LokDocumentRenderer) {
                    viewModelScope.launch {
                        r.invalidations.collect { tick ->
                            // 캐시 비우고 UI tick 증가 → BitmapItem 재구성
                            runCatching { r.invalidateAll(h) }
                            _state.update { it.copy(invalidationTick = tick) }
                        }
                    }
                    viewModelScope.launch {
                        r.cursor.collect { c ->
                            _state.update { it.copy(officeCursor = c) }
                        }
                    }
                    viewModelScope.launch {
                        r.selection.collect { rects ->
                            _state.update { it.copy(officeSelection = rects) }
                        }
                    }
                }
                observeBookmarks(doc.uri.toString())
                refreshCurrentPageBookmark()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DocumentError) {
                _state.update { it.copy(loading = false, error = e) }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = DocumentError.Unknown(t)) }
            }
        }
    }

    fun onPageChanged(index: Int) {
        val s = _state.value
        if (index !in 0 until s.pageCount) return
        if (s.currentIndex == index) return
        _state.update { it.copy(currentIndex = index) }
        s.document?.let { doc ->
            viewModelScope.launch { repo.updateLastPage(doc.uri.toString(), index) }
        }
        refreshCurrentPageBookmark()
        // 검색 중이면 현재 페이지에 들어온 매치로 currentMatchIndex 동기화
        if (s.searchActive && s.searchMatches.isNotEmpty()) {
            val newMatch = s.searchMatches.indexOfFirst { it.pageIndex == index }
            if (newMatch >= 0) {
                _state.update { it.copy(currentMatchIndex = newMatch) }
            }
        }
    }

    suspend fun renderBitmap(index: Int, targetWidthPx: Int): Bitmap? {
        val r = renderer ?: return null
        val h = handle ?: return null
        return try {
            r.renderPage(h, index, targetWidthPx).bitmap
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun getPageText(index: Int): String? {
        pageTextCache[index]?.let { return it }
        val r = renderer ?: return null
        val h = handle ?: return null
        return try {
            val text = r.getPageText(h, index)
            if (text != null) pageTextCache[index] = text
            text
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun getPageImages(index: Int): List<android.graphics.Bitmap> {
        val r = renderer ?: return emptyList()
        val h = handle ?: return emptyList()
        return try {
            r.getPageImages(h, index)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            emptyList()
        }
    }

    suspend fun getPageElements(index: Int): List<com.wook.viewer.domain.model.PageElement>? {
        val r = renderer ?: return null
        val h = handle ?: return null
        return try {
            r.getPageElements(h, index)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }

    /** XLSX 시트 탭 등에서 호출. */
    fun selectSection(index: Int) {
        jumpToPage(index)
    }

    // ---- 검색 ----

    fun setSearchActive(active: Boolean) {
        if (!active) {
            searchJob?.cancel()
            _state.update {
                it.copy(
                    searchActive = false,
                    searchQuery = "",
                    searchMatches = emptyList(),
                    currentMatchIndex = -1,
                    searching = false
                )
            }
        } else {
            _state.update { it.copy(searchActive = true) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchMatches = emptyList(), currentMatchIndex = -1, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            val pageCount = _state.value.pageCount
            val matches = mutableListOf<SearchMatch>()
            for (i in 0 until pageCount) {
                val text = getPageText(i) ?: continue
                var idx = 0
                while (true) {
                    val found = text.indexOf(query, idx, ignoreCase = true)
                    if (found < 0) break
                    matches += SearchMatch(i, found, found + query.length)
                    idx = found + query.length
                }
                if (matches.size > MAX_MATCHES) break
            }
            // 결과: 첫 매치가 현재 페이지 이후 가장 가까운 매치
            val current = _state.value.currentIndex
            val firstFromHere = matches.indexOfFirst { it.pageIndex >= current }
            val pickIndex = if (firstFromHere >= 0) firstFromHere else if (matches.isNotEmpty()) 0 else -1
            _state.update {
                it.copy(
                    searchMatches = matches,
                    currentMatchIndex = pickIndex,
                    searching = false
                )
            }
            if (pickIndex >= 0 && matches.isNotEmpty()) {
                val targetPage = matches[pickIndex].pageIndex
                if (targetPage != _state.value.currentIndex) {
                    _state.update { it.copy(currentIndex = targetPage) }
                }
            }
        }
    }

    fun goToNextMatch() {
        val s = _state.value
        if (s.searchMatches.isEmpty()) return
        val next = (s.currentMatchIndex + 1).mod(s.searchMatches.size)
        val targetPage = s.searchMatches[next].pageIndex
        _state.update { it.copy(currentMatchIndex = next, currentIndex = targetPage) }
    }

    fun goToPrevMatch() {
        val s = _state.value
        if (s.searchMatches.isEmpty()) return
        val prev = if (s.currentMatchIndex <= 0) s.searchMatches.size - 1 else s.currentMatchIndex - 1
        val targetPage = s.searchMatches[prev].pageIndex
        _state.update { it.copy(currentMatchIndex = prev, currentIndex = targetPage) }
    }

    /** 현재 페이지(index)에 해당하는 매치 범위 반환 — TextPage 하이라이트용. */
    fun matchesForPage(pageIndex: Int): List<IntRange> =
        _state.value.searchMatches
            .filter { it.pageIndex == pageIndex }
            .map { it.rangeStart until it.rangeEnd }

    /** 현재 활성 매치의 범위 (currentMatchIndex가 가리키는 매치) — 다른 색으로 강조용. */
    fun activeMatchRange(pageIndex: Int): IntRange? {
        val s = _state.value
        if (s.currentMatchIndex < 0 || s.currentMatchIndex >= s.searchMatches.size) return null
        val m = s.searchMatches[s.currentMatchIndex]
        return if (m.pageIndex == pageIndex) m.rangeStart until m.rangeEnd else null
    }

    // ---- 북마크 ----

    private fun observeBookmarks(uriString: String) {
        bookmarkJob?.cancel()
        bookmarkJob = viewModelScope.launch {
            repo.observeBookmarks(uriString).collect { list ->
                _state.update { it.copy(bookmarks = list) }
            }
        }
    }

    private fun refreshCurrentPageBookmark() {
        val s = _state.value
        val uri = s.document?.uri?.toString() ?: return
        viewModelScope.launch {
            val isBm = repo.isBookmarked(uri, s.currentIndex)
            _state.update { it.copy(currentPageBookmarked = isBm) }
        }
    }

    fun toggleCurrentBookmark() {
        val s = _state.value
        val uri = s.document?.uri?.toString() ?: return
        viewModelScope.launch {
            val nowBookmarked = repo.toggleBookmark(uri, s.currentIndex)
            _state.update { it.copy(currentPageBookmarked = nowBookmarked) }
        }
    }

    fun removeBookmarkAt(pageIndex: Int) {
        val uri = _state.value.document?.uri?.toString() ?: return
        viewModelScope.launch {
            repo.removeBookmark(uri, pageIndex)
            if (pageIndex == _state.value.currentIndex) {
                _state.update { it.copy(currentPageBookmarked = false) }
            }
        }
    }

    fun jumpToPage(pageIndex: Int) {
        val s = _state.value
        if (pageIndex !in 0 until s.pageCount) return
        _state.update { it.copy(currentIndex = pageIndex) }
        s.document?.let { doc ->
            viewModelScope.launch { repo.updateLastPage(doc.uri.toString(), pageIndex) }
        }
        refreshCurrentPageBookmark()
    }

    // ---- 편집 ----

    /** 텍스트 포맷(.txt/.md) 만 편집 가능. */
    fun isEditableFormat(): Boolean {
        val fmt = _state.value.document?.format ?: return false
        return fmt == com.wook.viewer.domain.model.DocumentFormat.PLAIN_TEXT ||
            fmt == com.wook.viewer.domain.model.DocumentFormat.MARKDOWN
    }

    fun enterEditMode() {
        if (!isEditableFormat()) return
        viewModelScope.launch {
            // 모든 페이지 텍스트를 합쳐 단일 편집 버퍼로
            val pages = _state.value.pageCount
            val sb = StringBuilder()
            for (i in 0 until pages) {
                getPageText(i)?.let { sb.append(it) }
            }
            _state.update { it.copy(editMode = true, editedText = sb.toString()) }
        }
    }

    fun exitEditMode(discardChanges: Boolean = true) {
        _state.update {
            it.copy(editMode = false, editedText = if (discardChanges) "" else it.editedText)
        }
    }

    fun onTextEdited(newText: String) {
        _state.update { it.copy(editedText = newText) }
    }

    /** 기존 URI 에 덮어쓰기. */
    fun saveCurrent() {
        val s = _state.value
        val uri = s.document?.uri ?: return
        if (!s.editMode) return
        _state.update { it.copy(saving = true, saveResult = null) }
        viewModelScope.launch {
            val result = runCatching { repo.writeTextContent(uri, s.editedText) }
            _state.update {
                it.copy(
                    saving = false,
                    saveResult = result.fold(
                        onSuccess = { SaveResult.Success("저장 완료") },
                        onFailure = { e -> SaveResult.Failure("저장 실패: ${e.message ?: e.javaClass.simpleName}") }
                    )
                )
            }
            // 저장 후 편집 모드 종료 + 파일 재로드
            if (result.isSuccess) {
                _state.update { it.copy(editMode = false) }
                load(uri)
            }
        }
    }

    /** 새 URI 에 다른 이름으로 저장. UI 가 SAF CreateDocument 결과 URI 전달. */
    fun saveAs(newUri: Uri) {
        val s = _state.value
        if (!s.editMode) return
        _state.update { it.copy(saving = true, saveResult = null) }
        viewModelScope.launch {
            val result = runCatching { repo.writeTextContent(newUri, s.editedText) }
            _state.update {
                it.copy(
                    saving = false,
                    saveResult = result.fold(
                        onSuccess = { SaveResult.Success("다른 이름으로 저장 완료") },
                        onFailure = { e -> SaveResult.Failure("저장 실패: ${e.message ?: e.javaClass.simpleName}") }
                    )
                )
            }
            // 저장 성공 시 새 파일로 전환
            if (result.isSuccess) {
                _state.update { it.copy(editMode = false) }
                load(newUri)
            }
        }
    }

    fun clearSaveResult() {
        _state.update { it.copy(saveResult = null) }
    }

    // ---- Office 포맷 내보내기 (LOK saveAs) ----

    /** LOK 가 활성화돼있고 Office 포맷(DOCX/PPTX/XLSX) 인 경우만 내보내기 가능. */
    fun canExportOffice(): Boolean {
        val fmt = _state.value.document?.format ?: return false
        return fmt in LOK_SUPPORTED_FORMATS && renderer is LokDocumentRenderer
    }

    /** 원본 포맷의 LOK 단축명. saveCurrentDocument 호출에 사용. */
    private fun currentLokFormat(): String? = when (_state.value.document?.format) {
        DocumentFormat.DOCX -> "docx"
        DocumentFormat.PPTX -> "pptx"
        DocumentFormat.XLSX -> "xlsx"
        else -> null
    }

    // ---- Office 직접 편집 (LOK postKey/postMouse) ----

    /** Office 직접 편집 진입 가능 — LOK 활성화된 Office 파일만. */
    fun canEditOffice(): Boolean = canExportOffice()

    fun enterOfficeEditMode() {
        if (!canEditOffice()) return
        _state.update { it.copy(officeEditMode = true) }
        // 사용 가능 글꼴 목록 로드 (한 번만)
        if (_state.value.officeFonts.isEmpty()) {
            val r = renderer as? LokDocumentRenderer ?: return
            val h = handle ?: return
            viewModelScope.launch {
                val fonts = r.getAvailableFonts(h)
                _state.update {
                    it.copy(officeFonts = fonts.ifEmpty { DEFAULT_OFFICE_FONTS })
                }
            }
        }
    }

    fun exitOfficeEditMode() {
        _state.update { it.copy(officeEditMode = false) }
    }

    /**
     * 화면 좌표 (xPx, yPx) → LOK 커서 위치.
     * BitmapItem 이 측정된 widthPx/heightPx 와 함께 호출.
     */
    fun postOfficeTap(pageIndex: Int, xPx: Int, yPx: Int, widthPx: Int, heightPx: Int) {
        val r = renderer as? LokDocumentRenderer ?: return
        val h = handle ?: return
        viewModelScope.launch {
            r.postMouseTap(h, pageIndex, xPx, yPx, widthPx, heightPx)
        }
    }

    /** ASCII/유니코드 문자 입력. lokKeyCode=0 (일반 문자). */
    fun postOfficeChar(charCode: Int) {
        val r = renderer as? LokDocumentRenderer ?: return
        val h = handle ?: return
        viewModelScope.launch { r.postKey(h, charCode, 0) }
    }

    /** 특수키 입력 (Backspace=1283, Return=1280, Tab=1282, Delete=1286 등). */
    fun postOfficeSpecialKey(lokKeyCode: Int) {
        val r = renderer as? LokDocumentRenderer ?: return
        val h = handle ?: return
        viewModelScope.launch { r.postKey(h, 0, lokKeyCode) }
    }

    /** `.uno:Bold`, `.uno:Italic`, `.uno:Underline` 등 서식 명령. */
    fun postOfficeUnoCommand(command: String, arguments: String? = null) {
        val r = renderer as? LokDocumentRenderer ?: return
        val h = handle ?: return
        viewModelScope.launch { r.postUnoCommand(h, command, arguments) }
    }

    /** 글꼴 크기 변경 — 단위는 pt. */
    fun setOfficeFontSize(sizePt: Float) {
        val args = """{"FontHeight.Height":{"type":"float","value":"$sizePt"}}"""
        postOfficeUnoCommand(".uno:FontHeight", args)
    }

    /** 글자 색상 변경 — rgb 0xRRGGBB (long). 0xFFFFFFFF = -1 = 자동. */
    fun setOfficeFontColor(rgb: Long) {
        val args = """{"FontColor.Color":{"type":"long","value":"$rgb"}}"""
        postOfficeUnoCommand(".uno:FontColor", args)
    }

    /** 글자 배경(형광펜) 색상 — rgb 0xRRGGBB (long). 0xFFFFFFFF = -1 = 없음. */
    fun setOfficeBackColor(rgb: Long) {
        val args = """{"BackColor.Color":{"type":"long","value":"$rgb"}}"""
        postOfficeUnoCommand(".uno:BackColor", args)
    }

    /** 글꼴 종류 변경. */
    fun setOfficeFontName(fontName: String) {
        // JSON 값에 들어가는 글꼴명 — 따옴표/역슬래시 이스케이프
        val escaped = fontName.replace("\\", "\\\\").replace("\"", "\\\"")
        val args = """{"CharFontName.FontName":{"type":"string","value":"$escaped"}}"""
        postOfficeUnoCommand(".uno:CharFontName", args)
    }

    /**
     * 길게 누른 위치에 커서 이동 후 단어 선택.
     * UI 가 long-press 검출 시 호출.
     */
    fun officeLongPressSelectWord(
        pageIndex: Int,
        xPx: Int,
        yPx: Int,
        widthPx: Int,
        heightPx: Int
    ) {
        val r = renderer as? LokDocumentRenderer ?: return
        val h = handle ?: return
        viewModelScope.launch {
            r.postMouseTap(h, pageIndex, xPx, yPx, widthPx, heightPx)
            r.postUnoCommand(h, ".uno:SelectWord")
        }
    }

    /**
     * 현재 선택된 텍스트를 Android clipboard 로 복사.
     * UI 가 ClipboardManager 를 직접 다룰 수 없으니 callback 으로 전달.
     */
    fun copyOfficeSelection(onCopied: (text: String?) -> Unit) {
        val r = renderer as? LokDocumentRenderer ?: return
        val h = handle ?: return
        viewModelScope.launch {
            val text = r.getSelectedText(h)
            onCopied(text)
        }
    }

    /** Android clipboard 텍스트를 LOK 에 붙여넣기. UI 가 ClipboardManager 에서 가져와 전달. */
    fun pasteOfficeText(text: String) {
        val r = renderer as? LokDocumentRenderer ?: return
        val h = handle ?: return
        if (text.isBlank()) return
        viewModelScope.launch { r.pasteText(h, text) }
    }

    /** 현재 문서를 원본 URI 에 저장 (overwrite). LOK 가 임시 파일에 저장 후 SAF 복사. */
    fun saveOfficeEdits() {
        val s = _state.value
        val r = renderer as? LokDocumentRenderer ?: return
        val h = handle ?: return
        val uri = s.document?.uri ?: return
        val fmt = currentLokFormat() ?: return
        _state.update { it.copy(saving = true, saveResult = null) }
        viewModelScope.launch {
            val result = runCatching {
                val tmp = File.createTempFile("save_", ".$fmt", appContext.cacheDir)
                try {
                    if (!r.saveCurrentDocument(h, tmp, fmt)) {
                        throw IOException("LOK saveAs returned false")
                    }
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        tmp.inputStream().use { it.copyTo(out) }
                    } ?: throw IOException("openOutputStream returned null for $uri")
                } finally {
                    runCatching { tmp.delete() }
                }
            }
            _state.update {
                it.copy(
                    saving = false,
                    saveResult = result.fold(
                        onSuccess = { SaveResult.Success("저장 완료") },
                        onFailure = { e -> SaveResult.Failure("저장 실패: ${e.message ?: e.javaClass.simpleName}") }
                    )
                )
            }
            if (result.isSuccess) {
                _state.update { it.copy(officeEditMode = false) }
            }
        }
    }

    /** 현재 문서를 새 URI 에 같은 포맷으로 저장 (다른 이름으로 저장). */
    fun saveOfficeEditsAs(newUri: Uri) {
        val r = renderer as? LokDocumentRenderer ?: return
        val h = handle ?: return
        val fmt = currentLokFormat() ?: return
        _state.update { it.copy(saving = true, saveResult = null) }
        viewModelScope.launch {
            val result = runCatching {
                val tmp = File.createTempFile("save_", ".$fmt", appContext.cacheDir)
                try {
                    if (!r.saveCurrentDocument(h, tmp, fmt)) {
                        throw IOException("LOK saveAs returned false")
                    }
                    appContext.contentResolver.openOutputStream(newUri, "wt")?.use { out ->
                        tmp.inputStream().use { it.copyTo(out) }
                    } ?: throw IOException("openOutputStream returned null for $newUri")
                } finally {
                    runCatching { tmp.delete() }
                }
            }
            _state.update {
                it.copy(
                    saving = false,
                    saveResult = result.fold(
                        onSuccess = { SaveResult.Success("다른 이름으로 저장 완료") },
                        onFailure = { e -> SaveResult.Failure("저장 실패: ${e.message ?: e.javaClass.simpleName}") }
                    )
                )
            }
            if (result.isSuccess) {
                _state.update { it.copy(officeEditMode = false) }
                load(newUri)
            }
        }
    }

    /**
     * 현재 문서를 [lokFormat] 으로 변환해 [targetUri] 에 저장.
     *
     * LOK 는 임시 파일에만 저장 가능 → 캐시 파일에 저장 후 SAF URI 로 복사.
     */
    fun exportToFormat(targetUri: Uri, lokFormat: String, formatLabel: String) {
        val r = renderer
        val h = handle
        if (r !is LokDocumentRenderer || h == null) {
            _state.update {
                it.copy(saveResult = SaveResult.Failure("내보내기를 지원하지 않는 파일입니다"))
            }
            return
        }
        _state.update { it.copy(saving = true, saveResult = null) }
        viewModelScope.launch {
            val result = runCatching {
                val tmpFile = File.createTempFile("export_", ".$lokFormat", appContext.cacheDir)
                try {
                    val ok = r.saveAs(h, tmpFile, lokFormat)
                    if (!ok) throw IOException("LOK saveAs returned false")
                    appContext.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                        tmpFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IOException("openOutputStream returned null for $targetUri")
                    Timber.i("Office export 완료: $lokFormat → $targetUri")
                } finally {
                    runCatching { tmpFile.delete() }
                }
            }
            _state.update {
                it.copy(
                    saving = false,
                    saveResult = result.fold(
                        onSuccess = { SaveResult.Success("$formatLabel 로 저장 완료") },
                        onFailure = { e ->
                            SaveResult.Failure("저장 실패: ${e.message ?: e.javaClass.simpleName}")
                        }
                    )
                )
            }
        }
    }

    private fun closeCurrentHandle() {
        val r = renderer
        val h = handle
        renderer = null
        handle = null
        if (r != null && h != null) {
            appScope.launch(Dispatchers.IO) {
                runCatching { r.close(h) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeCurrentHandle()
    }

    private companion object {
        const val MAX_MATCHES = 500

        /** LOK 글꼴 조회 실패 시 폴백 — LibreOffice 번들 글꼴 + 흔한 한글 글꼴명. */
        val DEFAULT_OFFICE_FONTS = listOf(
            "Liberation Sans", "Liberation Serif", "Liberation Mono",
            "Carlito", "Caladea", "DejaVu Sans", "DejaVu Serif",
            "굴림", "돋움", "바탕", "궁서", "맑은 고딕", "함초롬바탕", "함초롬돋움"
        )
    }
}
