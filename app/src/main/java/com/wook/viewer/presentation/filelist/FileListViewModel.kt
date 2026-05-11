package com.wook.viewer.presentation.filelist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wook.viewer.domain.model.RecentDocument
import com.wook.viewer.domain.model.RecentSortOrder
import com.wook.viewer.domain.repository.DocumentRepository
import com.wook.viewer.domain.usecase.ObserveRecentDocumentsUseCase
import com.wook.viewer.domain.usecase.OpenDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FileListUiState(
    val recent: List<RecentDocument> = emptyList(),
    val sortOrder: RecentSortOrder = RecentSortOrder.RECENT
)

sealed interface FileListEvent {
    data class OpenDocument(val uri: Uri) : FileListEvent
    data class ShowError(val message: String) : FileListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FileListViewModel @Inject constructor(
    private val observeRecent: ObserveRecentDocumentsUseCase,
    private val repo: DocumentRepository,
    private val openDocument: OpenDocumentUseCase
) : ViewModel() {

    private val sortOrder = MutableStateFlow(RecentSortOrder.RECENT)

    val state: StateFlow<FileListUiState> = combine(
        sortOrder,
        sortOrder.flatMapLatest { observeRecent(it) }
    ) { order, list ->
        FileListUiState(recent = list, sortOrder = order)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FileListUiState()
    )

    private val _events = MutableStateFlow<FileListEvent?>(null)
    val events: StateFlow<FileListEvent?> = _events.asStateFlow()

    fun setSortOrder(order: RecentSortOrder) {
        sortOrder.value = order
    }

    fun togglePin(item: RecentDocument) {
        viewModelScope.launch {
            repo.setPinned(item.uriString, !item.pinned)
        }
    }

    fun removeRecent(item: RecentDocument) {
        viewModelScope.launch {
            repo.removeRecent(item.uriString)
        }
    }

    fun onUriPicked(uri: Uri) {
        viewModelScope.launch {
            when (val r = openDocument(uri)) {
                is OpenDocumentUseCase.Result.Ok ->
                    _events.value = FileListEvent.OpenDocument(r.document.uri)
                is OpenDocumentUseCase.Result.Unsupported ->
                    _events.value = FileListEvent.ShowError("지원하지 않는 형식입니다: ${r.name}")
                is OpenDocumentUseCase.Result.NotFound -> {
                    val msg = "파일을 열 수 없습니다." +
                        (r.cause?.message?.let { "\n[debug] $it" } ?: "")
                    _events.value = FileListEvent.ShowError(msg)
                }
            }
        }
    }

    fun onRecentClicked(item: RecentDocument) {
        onUriPicked(Uri.parse(item.uriString))
    }

    fun consumeEvent() { _events.value = null }
}
