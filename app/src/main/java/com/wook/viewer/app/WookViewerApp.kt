package com.wook.viewer.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WookViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfigFlag.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}

// build.gradle 에서 buildconfig 비활성화 했으므로 자체 플래그
internal object BuildConfigFlag {
    const val DEBUG = true
}
