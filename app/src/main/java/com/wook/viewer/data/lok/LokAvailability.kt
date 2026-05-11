package com.wook.viewer.data.lok

import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibreOfficeKit 네이티브 라이브러리 가용성 런타임 체크.
 *
 * 빌드 단계의 통합:
 *   - 'liblo-native-code.so' 가 jniLibs/<abi>/ 에 존재해야 함
 *   - 별도 워크플로(.github/workflows/build-libreoffice-android.yml)로 빌드
 *   - 빌드 산출물(~100MB)은 git에 안 들어감 — GitHub Release/artifact로 배포
 *
 * 런타임 시:
 *   - [isAvailable] 이 System.loadLibrary 시도, 실패하면 false 반환
 *   - 결과를 캐싱해 반복 호출 시 빠르게 응답
 *   - false이면 RendererRegistry 가 LOK 렌더러를 라우팅하지 않음 → 기존 폴백 사용
 *
 * **현재 상태**: 네이티브 라이브러리 미빌드. 항상 false 반환.
 * Session 2 에서 LO Android 빌드를 실행하면 libs 가 들어오고 자동으로 사용 가능.
 */
@Singleton
class LokAvailability @Inject constructor() {

    private val cached = AtomicReference<Boolean?>(null)

    /**
     * 네이티브 라이브러리 로드 시도. 첫 호출에서만 실제 로드를 시도하고
     * 이후엔 캐시 값 반환. 스레드 안전.
     *
     * @return libs 로드 성공 시 true. 라이브러리 부재/링크 실패 시 false.
     */
    fun isAvailable(): Boolean {
        cached.get()?.let { return it }

        val result = synchronized(this) {
            cached.get() ?: run {
                val loaded = runCatching {
                    System.loadLibrary(LIB_NAME)
                    true
                }.getOrElse { e ->
                    Timber.i("LibreOfficeKit 네이티브 라이브러리 미가용: ${e.message}")
                    false
                }
                cached.set(loaded)
                loaded
            }
        }
        return result
    }

    private companion object {
        const val LIB_NAME = "lo-native-code"
    }
}
