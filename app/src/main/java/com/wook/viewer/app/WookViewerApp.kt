package com.wook.viewer.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WookViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 정식 릴리즈: 외부 저장소에 로그/크래시 파일을 생성하지 않음.
        // 디버깅이 필요할 때만 아래 한 줄 주석을 해제하면 logcat 에 출력.
        // Timber.plant(Timber.DebugTree())

        runCatching { PDFBoxResourceLoader.init(applicationContext) }
            .onFailure { Timber.e(it, "PDFBoxResourceLoader.init 실패") }
    }
}
