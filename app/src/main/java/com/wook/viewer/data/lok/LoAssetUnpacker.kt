package com.wook.viewer.data.lok

import android.content.Context
import android.content.res.AssetManager
import timber.log.Timber
import java.io.File

/**
 * LibreOffice 가 init 시점에 filesDir 에서 찾는 데이터 파일들 (services.rdb,
 * `types/` 아래 *.rdb, `registry/` 아래 설정, 폰트 등)을 APK `assets/unpack/` 에서 추출.
 *
 * LO Android 배포본은 LibreOfficeMainActivity 가 시작 시 한 번 이 step 을 수행.
 * 우리는 자체 Activity 구조라 따로 호출해야 한다.
 *
 * 동작:
 *   - APK assets/unpack/ 의 디렉토리 트리를 그대로 [Context.getFilesDir] 에 복사
 *   - 완료 후 marker 파일(.lo-unpack-done)을 만들어 두 번째 호출에서 skip
 *   - LO 버전 업그레이드 시 marker 를 지우면 재 unpack
 */
internal object LoAssetUnpacker {

    private const val SOURCE_ROOT = "unpack"
    private const val MARKER_FILE = ".lo-unpack-done"

    /**
     * @return true 면 unpack 성공 또는 이미 완료된 상태. false 면 실패 (LO init 시도 무의미).
     */
    fun ensureUnpacked(context: Context): Boolean {
        val filesDir = context.filesDir
        val marker = File(filesDir, MARKER_FILE)
        if (marker.exists()) {
            Timber.d("LO assets 이미 unpack 됨")
            return true
        }

        val am = context.assets
        val topLevel = runCatching { am.list(SOURCE_ROOT).orEmpty() }.getOrDefault(emptyArray())
        if (topLevel.isEmpty()) {
            Timber.w("APK assets/unpack/ 비어있음 — LO native libs/assets 동봉 안 된 것")
            return false
        }

        Timber.i("LO assets unpack 시작: ${topLevel.size} top-level entries → $filesDir")
        return runCatching {
            for (entry in topLevel) {
                extractTree(am, "$SOURCE_ROOT/$entry", File(filesDir, entry))
            }
            marker.writeText(System.currentTimeMillis().toString())
            Timber.i("LO assets unpack 완료")
            true
        }.getOrElse { e ->
            Timber.e(e, "LO assets unpack 실패")
            // 부분 unpack 상태 정리
            runCatching { marker.delete() }
            false
        }
    }

    private fun extractTree(am: AssetManager, srcPath: String, destFile: File) {
        val children = runCatching { am.list(srcPath).orEmpty() }.getOrDefault(emptyArray())
        if (children.isEmpty()) {
            // leaf — 파일로 복사
            destFile.parentFile?.mkdirs()
            am.open(srcPath).use { input ->
                destFile.outputStream().use { out -> input.copyTo(out) }
            }
        } else {
            // 디렉토리 — 재귀
            destFile.mkdirs()
            for (child in children) {
                extractTree(am, "$srcPath/$child", File(destFile, child))
            }
        }
    }
}
