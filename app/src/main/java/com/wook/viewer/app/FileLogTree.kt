package com.wook.viewer.app

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timber Tree — 모든 로그를 외부 파일에 즉시 flush 로 기록.
 *
 * 위치: `getExternalFilesDir(null)/logs/app.log`
 *   = `/storage/emulated/0/Android/data/com.wook.viewer/files/logs/app.log`
 *
 * 파일 매니저로 접근 가능 (권한 불필요). ADB 없이 logcat 대체.
 *
 * 한계:
 *   - native crash (SIGSEGV) 는 Java 로그 시스템 거치지 않음 — 마지막 Java 로그가 단서
 *   - 매 로그마다 file flush — 약간 느림. 디버그 단계에서만 사용 권장
 */
internal class FileLogTree(context: Context) : Timber.Tree() {

    private val logFile: File = File(
        File(context.getExternalFilesDir(null), "logs").apply { mkdirs() },
        "app.log"
    )

    private val timestampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        // 세션 시작 마커
        runCatching {
            appendLine("\n==== APP SESSION START: ${timestampFmt.format(Date())} ====\n")
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val ts = timestampFmt.format(Date())
        val level = priorityChar(priority)
        val tagStr = tag ?: "?"
        val line = buildString {
            append(ts).append(' ').append(level).append(' ')
            append(tagStr).append(": ").append(message)
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
        FileWriter(logFile, true).use { fw ->
            fw.write(line)
            fw.flush()
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
        val sw = java.io.StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
