package com.wook.viewer.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 미처 잡지 못한 Java 예외 발생 시 stack trace 를 공개 Downloads 폴더에 저장.
 *
 * 위치: `Download/WookViewer/crash-YYYYMMDD-HHmmss.txt`
 *
 * 사용자가 파일 매니저로 직접 접근 가능 (Android/data/ 처럼 숨김 안 됨).
 *
 * 한계: SIGSEGV/abort 같은 native crash 는 Java 핸들러로 못 잡음 — 그 경우엔
 * FileLogTree 가 남긴 마지막 로그가 단서.
 */
internal object CrashLogger {

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appCtx, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "crash-$ts.txt"
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
        val content = sw.toString()

        val uri: Uri? = runCatching {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/WookViewer"
                    )
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Files.getContentUri("external")
            }
            context.contentResolver.insert(collection, cv)
        }.getOrNull()

        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                Timber.e(throwable, "Crash saved to $uri")
            }
        }
    }
}
