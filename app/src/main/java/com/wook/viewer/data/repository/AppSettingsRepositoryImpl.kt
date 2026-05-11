package com.wook.viewer.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.wook.viewer.domain.model.AppSettings
import com.wook.viewer.domain.model.TextScale
import com.wook.viewer.domain.model.ThemeMode
import com.wook.viewer.domain.repository.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 설정 저장 — SharedPreferences 백킹.
 *
 * StateFlow 로 노출해 Compose에서 collectAsState 로 reactive 구독.
 * 외부 의존성(DataStore)을 피해 APK 사이즈를 키우지 않음.
 */
@Singleton
class AppSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : AppSettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadFromPrefs())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadFromPrefs(): AppSettings {
        val themeOrdinal = prefs.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.ordinal)
        val scaleOrdinal = prefs.getInt(KEY_TEXT_SCALE, TextScale.MEDIUM.ordinal)
        val useLok = prefs.getBoolean(KEY_USE_LOK, false)
        return AppSettings(
            themeMode = ThemeMode.entries.getOrElse(themeOrdinal) { ThemeMode.SYSTEM },
            textScale = TextScale.entries.getOrElse(scaleOrdinal) { TextScale.MEDIUM },
            useLibreOfficeForOffice = useLok
        )
    }

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode.ordinal).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    override fun setTextScale(scale: TextScale) {
        prefs.edit().putInt(KEY_TEXT_SCALE, scale.ordinal).apply()
        _settings.value = _settings.value.copy(textScale = scale)
    }

    override fun setUseLibreOffice(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_LOK, enabled).apply()
        _settings.value = _settings.value.copy(useLibreOfficeForOffice = enabled)
    }

    private companion object {
        const val PREFS_NAME = "wook_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_USE_LOK = "use_libreoffice"
    }
}
