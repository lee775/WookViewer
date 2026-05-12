package com.wook.viewer.app

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 미처 잡지 못한 예외가 발생하면 파일로 stack trace 저장.
 *
 * 위치: `getExternalFilesDir(null)/crashes/crash-YYYYMMDD-HHmmss.txt`
 *   = `/storage/emulated/0/Android/data/com.wook.viewer/files/crashes/...`
 *
 * 사용자가 파일 매니저로 접근 가능 (권한 불필요, scoped storage 호환).
 * Application.onCreate 에서 [install] 호출.
 *
 * 한계: SIGSEGV 같은 native crash 는 Java exception handler 로 못 잡음.
 * 그런 경우엔 logcat 만이 방법.
 */
internal object CrashLogger {

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val crashDir = File(appCtx.getExternalFilesDir(null), "crashes").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(crashDir, "crash-$ts.txt")
                val sw = StringWriter()
                PrintWriter(sw).use { pw ->
                    pw.println("WookViewer Crash Log")
                    pw.println("Time: $ts")
                    pw.println("Thread: ${thread.name}")
                    pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    pw.println("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
                    pw.println("---")
                    throwable.printStackTrace(pw)
                }
                file.writeText(sw.toString())
                Timber.e(throwable, "Crash saved to $file")
            }
            // 원래 핸들러로 위임 (앱 정상적으로 죽음)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
