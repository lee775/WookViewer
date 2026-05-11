package com.wook.viewer.presentation.settings

import androidx.lifecycle.ViewModel
import com.wook.viewer.domain.model.AppSettings
import com.wook.viewer.domain.model.TextScale
import com.wook.viewer.domain.model.ThemeMode
import com.wook.viewer.domain.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: AppSettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings

    fun setThemeMode(mode: ThemeMode) = repo.setThemeMode(mode)
    fun setTextScale(scale: TextScale) = repo.setTextScale(scale)
    fun setUseLibreOffice(enabled: Boolean) = repo.setUseLibreOffice(enabled)
}
