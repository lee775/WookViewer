package com.wook.viewer.domain.repository

import com.wook.viewer.domain.model.AppSettings
import com.wook.viewer.domain.model.TextScale
import com.wook.viewer.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface AppSettingsRepository {
    val settings: StateFlow<AppSettings>
    fun setThemeMode(mode: ThemeMode)
    fun setTextScale(scale: TextScale)
}
