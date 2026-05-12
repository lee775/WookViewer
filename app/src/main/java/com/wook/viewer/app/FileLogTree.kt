package com.wook.viewer.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timber Tree — 모든 로그를 **공개 Downloads 폴더** 에 즉시 기록.
 *
 * 위치: `Download/WookViewer/WookViewer-YYYYMMDD-HHmmss.log`
 *   파일 매니저 (My Files, 갤러리 등) 의 "Download" 폴더에서 바로 보임.
 *   권한 불필요 (자기 앱이 생성한 파일은 항상 access 가능).
 *
 * Android 10+ : MediaStore.Downloads API 사용 (scoped storage 호환)
 * Android < 10: 외부 저장소 직접 (오래된 동작, minSdk=26 이라 지원 범위 내)
 *
 * 매 세션마다 새 파일 생성 (이전 로그 보존).
 *
 * 한계:
 *   - SIGSEGV/abort 같은 native crash 는 Java 로그 시스템 거치지 않음
 *   - 매 로그마다 stream open/close — 약간 느림. 디버그 용도로만.
 */
internal class FileLogTree(context: Context) : Timber.Tree() {

    private val resolver = context.contentResolver
    private val timestampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logUri: Uri? = createLogFile()

    init {
        runCatching {
            appendLine("==== APP SESSION START: ${timestampFmt.format(Date())} ====\n")
            appendLine("Log location: ${logUri ?: "(failed to create)"}\n")
        }
    }

    private fun createLogFile(): Uri? = runCatching {
        val fileTs = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "WookViewer-$fileTs.log"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Download/WookViewer/ 하위
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
        resolver.insert(collection, cv)
    }.getOrNull()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val ts = timestampFmt.format(Date())
        val level = priorityChar(priority)
        val line = buildString {
            append(ts).append(' ').append(level).append(' ')
            append(tag ?: "?").append(": ").append(message)
            if (t != null) {
                append('\n')
                append(stackTraceString(t))
            }
            append('\n')
        }
        runCatching { appendLine(line) }
    }

    @Synchronized
    private fun appendLine(line: String) {
        val uri = logUri ?: return
        resolver.openOutputStream(uri, "wa")?.use { out ->
            out.write(line.toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    private fun priorityChar(priority: Int): Char = when (priority) {
        Log.VERBOSE -> 'V'
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        Log.ASSERT -> 'A'
        else -> '?'
    }

    private fun stackTraceString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
