package com.wook.viewer.data.lok

import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibreOfficeKit 네이티브 라이브러리 가용성 런타임 체크.
 *
 * LO Android는 단일 .so가 아닌 ~14개의 native lib을 의존성 순서대로
 * 로드해야 한다 (NSS 트리플 → c++_shared → lo-native-code).
 * 어느 하나라도 누락되면 UnsatisfiedLinkError.
 *
 * 빌드 단계의 통합:
 *   - 모든 .so 가 jniLibs/<abi>/ 에 존재해야 함
 *   - 별도 워크플로(.github/workflows/build-libreoffice-android.yml)로 빌드
 *   - 빌드 산출물(~100MB)은 git에 안 들어감 — GitHub Release/artifact로 배포
 *
 * 런타임 시:
 *   - [isAvailable] 이 모든 libs을 순차 로드. 모두 성공해야 true.
 *   - 결과를 캐싱해 반복 호출 시 빠르게 응답.
 *   - false이면 RendererRegistry 가 LOK 렌더러를 라우팅하지 않음.
 */
@Singleton
class LokAvailability @Inject constructor() {

    private val cached = AtomicReference<Boolean?>(null)

    fun isAvailable(): Boolean {
        cached.get()?.let { return it }

        val result = synchronized(this) {
            cached.get() ?: run {
                val loaded = loadAllLibs()
                cached.set(loaded)
                loaded
            }
        }
        return result
    }

    /**
     * LibreOfficeKit.NativeLibLoader.load() 와 동일한 순서로 로드 시도.
     * 어느 단계든 실패하면 false 반환 — 안전하게 폴백.
     */
    private fun loadAllLibs(): Boolean {
        for (lib in REQUIRED_LIBS) {
            try {
                Timber.d("loading lib: $lib")
                System.loadLibrary(lib)
            } catch (e: Throwable) {
                Timber.i("LO 라이브러리 로드 실패: $lib — ${e.javaClass.simpleName}: ${e.message}")
                return false
            }
        }
        Timber.i("LO 사전 라이브러리 ${REQUIRED_LIBS.size}개 모두 로드 OK")
        return true
    }

    private companion object {
        /**
         * 사전 검증용 — NSS 트리플 + c++_shared 만 로드한다.
         * lo-native-code.so 는 JNI_OnLoad 에서 LO assets 를 filesDir 에서 찾으므로
         * unpack 단계 이후에야 안전하게 로드 가능 → LibreOfficeKit 클래스 static block
         * (LibreOfficeKit.init 호출 시) 에 위임.
         */
        val REQUIRED_LIBS = listOf(
            "nspr4",
            "plds4",
            "plc4",
            "nssutil3",
            "freebl3",
            "sqlite3",
            "softokn3",
            "nss3",
            "nssckbi",
            "nssdbm3",
            "smime3",
            "ssl3",
            "c++_shared"
            // "lo-native-code" 는 의도적으로 제외 — LokSession 이 unpack 후 로드.
        )
    }
}
