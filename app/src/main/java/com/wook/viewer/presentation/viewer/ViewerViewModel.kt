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
    val pageBitmap: Bitmap? = null,
    /** UI가 strings.xml로 매핑할 도메인 에러. */
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
    private var renderJob: Job? = null

    fun load(uri: Uri, targetWidthPx: Int) {
        if (_state.value.document?.uri == uri && _state.value.error == null) return
        // 진행 중이던 로드/렌더가 있으면 취소 — 이게 없으면 두 번째 load의 renderPage가
        // 첫 번째 renderJob을 cancel시키고 catch가 cancellation을 일반 에러로 보고함
        loadJob?.cancel()
        renderJob?.cancel()
        closeCurrentHandle()
        _state.update {
            it.copy(loading = true, error = null, pageBitmap = null, document = null, pageCount = 0)
        }

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
                renderPage(0, targetWidthPx)
            } catch (e: CancellationException) {
                // 정상적인 취소 (load/render 재진입 등) — 에러로 보고하지 않고 재던짐
                throw e
            } catch (e: DocumentError) {
                _state.update { it.copy(loading = false, error = e) }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = DocumentError.Unknown(t)) }
            }
        }
    }

    fun goToPage(index: Int, targetWidthPx: Int) {
        val s = _state.value
        if (index !in 0 until s.pageCount) return
        _state.update { it.copy(currentIndex = index) }
        renderPage(index, targetWidthPx)
        s.document?.let { doc ->
            viewModelScope.launch { repo.updateLastPage(doc.uri.toString(), index) }
        }
    }

    private fun renderPage(index: Int, targetWidthPx: Int) {
        val r = renderer ?: return
        val h = handle ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            try {
                val rendered = r.renderPage(h, index, targetWidthPx)
                // 성공 시 이전 에러도 함께 클리어 (이전 렌더 실패 후 재시도 케이스)
                _state.update { it.copy(pageBitmap = rendered.bitmap, error = null) }
            } catch (e: CancellationException) {
                throw e  // 취소는 에러 아님
            } catch (e: DocumentError) {
                _state.update { it.copy(error = e) }
            } catch (t: Throwable) {
                _state.update { it.copy(error = DocumentError.Unknown(t)) }
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
}
