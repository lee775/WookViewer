package com.wook.viewer.data.lok

import android.app.Activity
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibreOfficeKit 세션 — Office 싱글톤 + 초기화 라이프사이클.
 *
 * LOK는 프로세스 당 1회 초기화하면 충분하다. [LibreOfficeKit.init] 는 dataDir,
 * cacheDir, apkFile, AssetManager 를 가져와 native 측에 전달한다.
 * 이 정보는 Activity의 ApplicationInfo 에서 수집되므로 Activity 컨텍스트가 필요.
 *
 * 사용 흐름:
 *   1. MainActivity.onCreate 에서 [tryInit] 호출 (Activity 전달)
 *   2. 성공 시 [getOffice] 가 Office 인스턴스 반환
 *   3. Renderer 가 office.documentLoad(uri) 로 문서 열기
 *
 * native lib 미존재 시 [tryInit] 가 false 반환하며 [LokAvailability] 와 일관성 유지.
 */
@Singleton
class LokSession @Inject constructor(
    private val lokAvailability: LokAvailability
) {

    private val officeRef = AtomicReference<Office?>(null)
    @Volatile private var initialized = false

    fun isInitialized(): Boolean = initialized && officeRef.get() != null

    /**
     * LOK 초기화 시도. 이미 초기화되어 있으면 즉시 true.
     * native lib 미가용 또는 init 실패 시 false.
     */
    @Synchronized
    fun tryInit(activity: Activity): Boolean {
        if (initialized) return officeRef.get() != null
        if (!lokAvailability.isAvailable()) {
            initialized = true  // 시도 완료 표시 — 재시도 방지
            return false
        }

        return runCatching {
            LibreOfficeKit.init(activity)
            val handle = LibreOfficeKit.getLibreOfficeKitHandle()
            requireNotNull(handle) { "LibreOfficeKit handle is null after init" }
            val office = Office(handle)
            officeRef.set(office)
            initialized = true
            Timber.i("LibreOfficeKit 초기화 완료")
            true
        }.getOrElse { e ->
            Timber.e(e, "LibreOfficeKit 초기화 실패")
            initialized = true
            false
        }
    }

    fun getOffice(): Office? = officeRef.get()

    /** 앱 종료 시점 (선택적). LOK는 보통 프로세스 종료까지 살려둔다. */
    @Synchronized
    fun destroy() {
        officeRef.getAndSet(null)?.let { office ->
            runCatching { office.destroy() }.onFailure {
                Timber.w(it, "Office.destroy 실패")
            }
        }
    }
}
