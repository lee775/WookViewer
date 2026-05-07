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
    private var renderJob: Job? = null

    fun load(uri: Uri, targetWidthPx: Int) {
        if (_state.value.document?.uri == uri) return
        closeCurrentHandle()
        _state.update { it.copy(loading = true, error = null, pageBitmap = null) }

        viewModelScope.launch {
            val doc = try {
                repo.resolveDocument(uri)
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = DocumentError.IoError(t)) }
                return@launch
            }
            if (doc == null) {
                _state.update { it.copy(loading = false, error = DocumentError.IoError()) }
                return@launch
            }
            val r = registry.rendererFor(doc.format)
            if (r == null) {
                _state.update {
                    it.copy(loading = false, document = doc, error = DocumentError.UnsupportedVariant(doc.format.displayName))
                }
                return@launch
            }

            try {
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
            } catch (e: DocumentError) {
                _state.update { it.copy(loading = false, document = doc, error = e) }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, document = doc, error = DocumentError.Unknown(t)) }
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
                _state.update { it.copy(pageBitmap = rendered.bitmap) }
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
