package com.wook.viewer.presentation

import androidx.lifecycle.ViewModel
import com.wook.viewer.domain.model.AppSettings
import com.wook.viewer.domain.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** 앱 전역 설정 노출 — MainActivity가 Theme 적용을 위해 collect. */
@HiltViewModel
class AppViewModel @Inject constructor(
    repo: AppSettingsRepository
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings
}
