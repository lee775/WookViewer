package com.wook.viewer.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WookViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfigFlag.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // PdfBox-Android는 폰트/리소스를 앱 캐시에 풀어둬야 동작
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
            .onFailure { Timber.e(it, "PDFBoxResourceLoader.init 실패") }
    }
}

// build.gradle 에서 buildconfig 비활성화 했으므로 자체 플래그
internal object BuildConfigFlag {
    const val DEBUG = true
}
