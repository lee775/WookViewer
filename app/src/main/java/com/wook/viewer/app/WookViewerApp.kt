package com.wook.viewer.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WookViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 크래시 발생 시 stack trace 를 외부 파일로 저장 (logcat 대체)
        CrashLogger.install(this)

        // 모든 Timber 로그를 외부 파일에도 기록 — ADB 없이 디버깅 가능
        // 위치: /storage/emulated/0/Android/data/com.wook.viewer/files/logs/app.log
        Timber.plant(FileLogTree(this))
        if (BuildConfigFlag.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("WookViewerApp.onCreate — device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, sdk=${android.os.Build.VERSION.SDK_INT}, abis=${android.os.Build.SUPPORTED_ABIS.joinToString()}")

        // PdfBox-Android는 폰트/리소스를 앱 캐시에 풀어둬야 동작
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
            .onFailure { Timber.e(it, "PDFBoxResourceLoader.init 실패") }
    }
}

// build.gradle 에서 buildconfig 비활성화 했으므로 자체 플래그
internal object BuildConfigFlag {
    const val DEBUG = true
}
