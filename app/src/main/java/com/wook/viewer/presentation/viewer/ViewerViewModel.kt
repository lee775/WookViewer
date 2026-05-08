package com.wook.viewer.presentation.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wook.viewer.di.ApplicationScope
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.Document
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

data class ViewerUiState(
    val loading: Boolean = false,
    val document: Document? = null,
    val pageCount: Int = 0,
    val currentIndex: Int = 0,
    val error: DocumentError? = null
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

    /** 문서 열기 — 메타데이터/페이지 수만 결정. 실제 렌더는 PageView에서 lazy하게. */
    fun load(uri: Uri) {
        if (_state.value.document?.uri == uri && _state.value.error == null) return
        loadJob?.cancel()
        closeCurrentHandle()
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: DocumentError) {
                _state.update { it.copy(loading = false, error = e) }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = DocumentError.Unknown(t)) }
            }
        }
    }

    /** Pager가 페이지 변경 시 호출. 최근 문서 DB에 마지막 페이지 갱신. */
    fun onPageChanged(index: Int) {
        val s = _state.value
        if (index !in 0 until s.pageCount) return
        if (s.currentIndex == index) return
        _state.update { it.copy(currentIndex = index) }
        s.document?.let { doc ->
            viewModelScope.launch { repo.updateLastPage(doc.uri.toString(), index) }
        }
    }

    /** PageView가 호출 — 비트맵 페이지 렌더 (PDF). 실패 시 null. */
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

    /** PageView가 호출 — 텍스트 페이지 (HWP/DOCX/PPTX). 미지원 포맷이면 null. */
    suspend fun getPageText(index: Int): String? {
        val r = renderer ?: return null
        val h = handle ?: return null
        return try {
            r.getPageText(h, index)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
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
}
