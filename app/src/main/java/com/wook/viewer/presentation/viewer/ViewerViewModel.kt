package com.wook.viewer.presentation.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wook.viewer.di.ApplicationScope
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.Bookmark
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.model.RenderingFidelity
import com.wook.viewer.domain.repository.DocumentHandle
import com.wook.viewer.domain.repository.DocumentRenderer
import com.wook.viewer.domain.repository.DocumentRepository
import com.wook.viewer.domain.repository.RendererRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 페이지 내에서의 검색 매치 — 페이지 인덱스 + 텍스트 내 [start, end) 범위. */
data class SearchMatch(
    val pageIndex: Int,
    val rangeStart: Int,
    val rangeEnd: Int
)

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
    @ApplicationScope private val appScope: CoroutineScope
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
        loadJob?.cancel()
        searchJob?.cancel()
        bookmarkJob?.cancel()
        closeCurrentHandle()
        pageTextCache.clear()
        _state.update { ViewerUiState(loading = true) }

        loadJob = viewModelScope.launch {
            try {
                val doc = repo.resolveDocument(uri)
                if (doc == null) {
                    _state.update { it.copy(loading = false, error = DocumentError.IoError()) }
                    return@launch
                }
                val r = registry.rendererFor(doc.format)
                if (r == null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            document = doc,
                            error = DocumentError.UnsupportedVariant(doc.format.displayName)
                        )
                    }
                    return@launch
                }

                val h = r.open(uri)
                renderer = r
                handle = h
                val count = r.pageCount(h)
                _state.update {
                    it.copy(
                        loading = false,
                        document = doc,
                        pageCount = count,
                        currentIndex = 0
                    )
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
    }
}
