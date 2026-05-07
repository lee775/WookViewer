package com.wook.viewer.presentation.filelist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wook.viewer.domain.model.RecentDocument
import com.wook.viewer.domain.usecase.ObserveRecentDocumentsUseCase
import com.wook.viewer.domain.usecase.OpenDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FileListUiState(
    val recent: List<RecentDocument> = emptyList()
)

sealed interface FileListEvent {
    data class OpenDocument(val uri: Uri) : FileListEvent
    data class ShowError(val message: String) : FileListEvent
}

@HiltViewModel
class FileListViewModel @Inject constructor(
    observeRecent: ObserveRecentDocumentsUseCase,
    private val openDocument: OpenDocumentUseCase
) : ViewModel() {

    val state: StateFlow<FileListUiState> = observeRecent()
        .map { FileListUiState(recent = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FileListUiState()
        )

    private val _events = MutableStateFlow<FileListEvent?>(null)
    val events: StateFlow<FileListEvent?> = _events.asStateFlow()

    fun onUriPicked(uri: Uri) {
        viewModelScope.launch {
            when (val r = openDocument(uri)) {
                is OpenDocumentUseCase.Result.Ok ->
                    _events.value = FileListEvent.OpenDocument(r.document.uri)
                is OpenDocumentUseCase.Result.Unsupported ->
                    _events.value = FileListEvent.ShowError("지원하지 않는 형식입니다: ${r.name}")
                OpenDocumentUseCase.Result.NotFound ->
                    _events.value = FileListEvent.ShowError("파일을 열 수 없습니다.")
            }
        }
    }

    fun onRecentClicked(item: RecentDocument) {
        onUriPicked(Uri.parse(item.uriString))
    }

    fun consumeEvent() { _events.value = null }
}
