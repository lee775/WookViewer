package com.wook.viewer.data.lok

import android.app.Activity
import com.wook.viewer.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibreOfficeKit 세션 — Office 싱글톤 + 비동기 초기화 라이프사이클.
 *
 * 초기화는 native lib 로드 + LibreOfficeKit.init (2-5초 소요)이라 메인 스레드에서
 * 동기로 부르면 ANR. [tryInit] 는 즉시 반환하고 백그라운드에서 진행, [state] StateFlow 로
 * 진행 상태 노출. UI는 NotReady 상태일 때 LOK 토글을 노출하지 않거나 로딩 표시.
 *
 * 사용 흐름:
 *   1. MainActivity.onCreate → [tryInit] 호출 (비동기 시작, 즉시 반환)
 *   2. Renderer 가 [getOffice] 로 Office 인스턴스 얻기 (초기화 완료 후만 non-null)
 *   3. RendererRegistry 는 [isReady] 체크 후 LOK 라우팅 여부 결정
 *
 * native lib 미존재 시 [state] 가 [State.Unavailable] 로 즉시 전환.
 */
@Singleton
class LokSession @Inject constructor(
    private val lokAvailability: LokAvailability,
    @ApplicationScope private val appScope: CoroutineScope
) {

    enum class State {
        /** 초기화 시도 전. */
        Idle,
        /** 백그라운드에서 초기화 중. */
        Initializing,
        /** 초기화 완료, Office 인스턴스 사용 가능. */
        Ready,
        /** native lib 부재 또는 init 실패. */
        Unavailable
    }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val officeRef = AtomicReference<Office?>(null)
    private val initMutex = Mutex()

    fun isReady(): Boolean = _state.value == State.Ready && officeRef.get() != null

    /**
     * 비동기 초기화 시작. 즉시 반환. 이미 초기화 시도 중이거나 완료면 no-op.
     * Activity 컨텍스트가 필요하므로 MainActivity.onCreate 에서 호출.
     */
    fun tryInit(activity: Activity) {
        if (_state.value != State.Idle) return  // 이미 진행 또는 종료 상태
        _state.value = State.Initializing

        appScope.launch(Dispatchers.IO) {
            initMutex.withLock {
                if (_state.value != State.Initializing) return@withLock

                if (!lokAvailability.isAvailable()) {
                    _state.value = State.Unavailable
                    return@withLock
                }

                // LO native가 init 시점에 filesDir에서 찾는 파일들(services.rdb 등)을
                // assets/unpack/ → filesDir 로 복사. 이 단계 빼면 init 시 SIGSEGV.
                val unpacked = runCatching { LoAssetUnpacker.ensureUnpacked(activity) }
                    .getOrElse { e ->
                        Timber.e(e, "LO assets unpack 예외")
                        false
                    }
                if (!unpacked) {
                    Timber.w("LO assets unpack 실패 — init 시도 중단")
                    _state.value = State.Unavailable
                    return@withLock
                }

                val ok = runCatching {
                    // LibreOfficeKit.init 은 내부적으로 main thread 의존성 없음 (assets 읽기 등).
                    // 그러나 첫 호출이 무겁기 때문에 IO dispatcher 에서 실행.
                    LibreOfficeKit.init(activity)
                    val handle = LibreOfficeKit.getLibreOfficeKitHandle()
                    requireNotNull(handle) { "LibreOfficeKit handle is null after init" }
                    val office = Office(handle)
                    officeRef.set(office)
                    Timber.i("LibreOfficeKit 초기화 완료")
                    true
                }.getOrElse { e ->
                    Timber.e(e, "LibreOfficeKit 초기화 실패")
                    false
                }

                _state.value = if (ok) State.Ready else State.Unavailable
            }
        }
    }

    fun getOffice(): Office? = officeRef.get()

    /** 앱 종료 시 (선택적). LOK는 보통 프로세스 종료까지 살려둔다. */
    suspend fun destroy() {
        withContext(Dispatchers.IO) {
            initMutex.withLock {
                officeRef.getAndSet(null)?.let { office ->
                    runCatching { office.destroy() }
                        .onFailure { Timber.w(it, "Office.destroy 실패") }
                }
                _state.value = State.Idle
            }
        }
    }
}
