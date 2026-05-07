package com.wook.viewer.app

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * 런타임에 PackageManager로부터 버전/디버그 여부를 가져오는 유틸.
 *
 * BuildConfig를 끈 상태에서 동작 — 별도 코드 생성 불필요.
 */
object BuildInfo {

    fun versionName(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun displayLabel(context: Context): String {
        val v = versionName(context)
        return if (isDebuggable(context)) "v$v · debug" else "v$v"
    }
}
