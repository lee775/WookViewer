package com.wook.viewer.render.lok

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.wook.viewer.data.lok.LokSession
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.PageSize
import com.wook.viewer.domain.model.RenderedPage
import com.wook.viewer.domain.repository.DocumentHandle
import com.wook.viewer.domain.repository.DocumentRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.libreoffice.kit.DirectBufferAllocator
import org.libreoffice.kit.Document
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibreOfficeKit 기반 렌더러 — Office 파일 100% 재현 목표 (S3-S4 단계).
 *
 * 단위 시스템:
 *   - LOK 은 twips (1 inch = 1440 twips)
 *   - 우리 viewer는 px (target width 기반)
 *   - 변환: twips * (1/1440 inch) * (target_dpi px/inch)
 *
 * 페이지/파트 모델:
 *   - PPTX/XLSX: 각 part = 1 슬라이드/시트. setPart() 로 전환
 *   - DOCX: 일반적으로 1 part 이고 longa-page. parts > 1 이면 그대로 사용.
 *     (Calc 가 아닌 Writer 는 LOK 가 자동 페이지화 안 함)
 *
 * 견고화 (S3):
 *   - 모든 JNI 호출 runCatching 으로 보호 — 실패 시 DocumentError 로 변환
 *   - Mutex 로 동시 호출 직렬화 (LOK Document 가 thread-safe 하지 않음)
 *   - 비트맵 LRU 캐시 (3-5 페이지) — paintTile 재호출 회피
 *   - 캐시 dispose 시 비트맵 recycle
 */
@Singleton
class LokDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: LokSession
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = emptySet()

    /**
     * 편집 등으로 인해 비트맵 캐시가 무효화될 때 emit.
     * UI 는 이 값을 BitmapItem 의 key 에 추가해 강제 재구성/재렌더.
     */
    private val _invalidations = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val invalidations: SharedFlow<Long> = _invalidations.asSharedFlow()

    private fun emitInvalidation() {
        _invalidations.tryEmit(System.nanoTime())
    }

    /**
     * 현재 편집 중인 part 의 LOK 커서 위치 (정규화 0..1 비율).
     * null = 커서 없음 / 보이지 않음.
     */
    data class CursorState(
        val xFrac: Float,
        val yFrac: Float,
        val widthFrac: Float,
        val heightFrac: Float,
        val partIndex: Int,
        val visible: Boolean
    )

    private val _cursor = MutableStateFlow<CursorState?>(null)
    val cursor: StateFlow<CursorState?> = _cursor.asStateFlow()

    /**
     * 텍스트 선택 영역 — 정규화된 비율 (0..1).
     * payload "x,y,w,h;x,y,w,h;..." 형식의 사각형 목록을 파싱.
     */
    data class SelectionRect(
        val xFrac: Float,
        val yFrac: Float,
        val widthFrac: Float,
        val heightFrac: Float,
        val partIndex: Int,
    )

    private val _selection = MutableStateFlow<List<SelectionRect>>(emptyList())
    val selection: StateFlow<List<SelectionRect>> = _selection.asStateFlow()

    /** 한 페이지의 twips 좌표·크기. getPartPageRectangles 결과 파싱용. */
    private data class IntRect(val x: Int, val y: Int, val width: Int, val height: Int)

    private class Handle(
        override val uri: Uri,
        val document: Document,
        val tempFile: File,
        val docType: Int,
        val partCount: Int,
        /**
         * DOCX 처럼 single-part 인데 페이지가 여러 개인 경우의 페이지 rect 목록.
         * PPTX/XLSX (multi-part) 면 null — partCount 가 페이지 수와 동일.
         */
        val pageRects: List<IntRect>?,
        val mutex: Mutex = Mutex(),
        val bitmapCache: BitmapLruCache = BitmapLruCache(BITMAP_CACHE_SIZE_BYTES),
        /** paintTile/postMouseTap 에서 setPart 후 갱신 — callback 좌표 정규화에 사용. */
        val lastDocWidthTwips: AtomicLong = AtomicLong(0),
        val lastDocHeightTwips: AtomicLong = AtomicLong(0),
        val lastEditPart: AtomicInteger = AtomicInteger(0),
        /** 페이지 분할 DOCX 의 페이지별 텍스트 — getPageText 첫 호출 시 채워서 재사용. */
        @Volatile var cachedPageTexts: List<String>? = null,
    ) : DocumentHandle

    private data class CacheKey(val partIndex: Int, val widthPx: Int)

    /**
     * 페이지 비트맵 LRU 캐시.
     * 같은 page+width 조합은 재사용, evict 된 비트맵은 즉시 recycle 해서 메모리 회수.
     */
    private class BitmapLruCache(maxSize: Int) : LruCache<CacheKey, Bitmap>(maxSize) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.byteCount
        override fun entryRemoved(
            evicted: Boolean,
            key: CacheKey,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted && !oldValue.isRecycled) runCatching { oldValue.recycle() }
        }
    }

    override suspend fun open(uri: Uri): DocumentHandle = openInternal(uri, password = null)

    override suspend fun open(uri: Uri, password: String): DocumentHandle =
        openInternal(uri, password)

    private suspend fun openInternal(uri: Uri, password: String?): DocumentHandle =
        withContext(Dispatchers.IO) {
            val office = session.getOffice()
                ?: throw DocumentError.Unknown(IllegalStateException("LokSession 미초기화"))

            val tempFile = copyToCache(uri)
            val fileUrl = "file://${tempFile.absolutePath}"

            // 비밀번호가 있으면 먼저 설정 (LOK 은 다음 load 호출에서 사용)
            if (!password.isNullOrEmpty()) {
                runCatching { office.setDocumentPassword(fileUrl, password) }
                    .onFailure { Timber.w(it, "setDocumentPassword 실패") }
            }

            val document = runCatching { office.documentLoad(fileUrl) }
                .getOrElse {
                    tempFile.delete()
                    Timber.e(it, "documentLoad 예외")
                    throw DocumentError.Corrupted(it)
                } ?: run {
                    val err = runCatching { office.error }.getOrNull().orEmpty()
                    tempFile.delete()
                    throw classifyOpenError(err, password != null)
                }

            runCatching { document.initializeForRendering() }
                .onFailure { Timber.w(it, "initializeForRendering 실패 — 일부 렌더 결함 가능") }

            val docType = runCatching { document.documentType }.getOrDefault(Document.DOCTYPE_OTHER)
            val partCount = runCatching { document.parts.coerceAtLeast(1) }.getOrDefault(1)

            // single-part Writer 문서면 페이지 분할 → getPartPageRectangles
            val pageRects: List<IntRect>? = if (partCount == 1 && docType == Document.DOCTYPE_TEXT) {
                runCatching {
                    val raw = document.partPageRectangles
                    parsePageRects(raw).takeIf { it.size > 1 }
                }.onFailure { Timber.w(it, "getPartPageRectangles 실패") }.getOrNull()
            } else null

            Timber.i(
                "LOK 문서 열기: type=$docType, parts=$partCount, pages=${pageRects?.size ?: partCount}, file=${tempFile.name}"
            )

            val handle = Handle(uri, document, tempFile, docType, partCount, pageRects)

            // message callback — tile invalidation + cursor 위치/표시 추적
            runCatching {
                document.setMessageCallback { signalNumber, payload ->
                    when (signalNumber) {
                        Document.CALLBACK_INVALIDATE_TILES -> emitInvalidation()
                        Document.CALLBACK_INVALIDATE_VISIBLE_CURSOR -> {
                            updateCursorFromPayload(handle, payload)
                        }
                        Document.CALLBACK_CURSOR_VISIBLE -> {
                            val vis = payload?.trim() == "true"
                            _cursor.value = _cursor.value?.copy(visible = vis)
                        }
                        Document.CALLBACK_TEXT_SELECTION -> {
                            updateSelectionFromPayload(handle, payload)
                        }
                    }
                }
            }.onFailure { Timber.w(it, "setMessageCallback 실패 — 편집 후 재렌더 안 될 수 있음") }

            handle
        }

    /** "x,y,w,h; x,y,w,h; ..." → IntRect 목록. */
    private fun parsePageRects(payload: String?): List<IntRect> {
        if (payload.isNullOrBlank()) return emptyList()
        return payload.split(';').mapNotNull { rectStr ->
            val nums = rectStr.split(',').mapNotNull { it.trim().toIntOrNull() }
            if (nums.size >= 4 && nums[2] > 0 && nums[3] > 0) {
                IntRect(nums[0], nums[1], nums[2], nums[3])
            } else null
        }
    }

    /**
     * 절대 twips 좌표가 어느 페이지에 속하는지 찾기.
     * 정확히 포함되는 페이지가 없으면 y 기준 가장 가까운 페이지.
     */
    private fun findPageIndex(handle: Handle, xTwips: Int, yTwips: Int): Int {
        val rects = handle.pageRects ?: return handle.lastEditPart.get()
        rects.forEachIndexed { i, r ->
            if (yTwips >= r.y && yTwips < r.y + r.height &&
                xTwips >= r.x && xTwips < r.x + r.width
            ) return i
        }
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        rects.forEachIndexed { i, r ->
            val centerY = r.y + r.height / 2
            val dist = kotlin.math.abs(centerY - yTwips)
            if (dist < bestDist) { bestDist = dist; bestIdx = i }
        }
        return bestIdx
    }

    /**
     * TEXT_SELECTION payload 형식: "x,y,w,h; x,y,w,h; ..." (twips, 세미콜론 구분)
     * 빈 문자열 또는 "EMPTY" 면 선택 해제.
     * 페이지 분할된 DOCX 면 각 rect 의 partIndex 를 그 rect 가 속한 페이지로 설정.
     */
    private fun updateSelectionFromPayload(handle: Handle, payload: String?) {
        if (payload.isNullOrBlank() || payload.trim() == "EMPTY") {
            _selection.value = emptyList()
            return
        }
        if (handle.pageRects != null) {
            // 페이지 분할 — 각 selection rect 가 어느 페이지에 속하는지 찾아 정규화
            val rects = payload.split(';').mapNotNull { rectStr ->
                val nums = rectStr.split(',').mapNotNull { it.trim().toIntOrNull() }
                if (nums.size < 4) return@mapNotNull null
                val absX = nums[0]; val absY = nums[1]
                val w = nums[2]; val h = nums[3]
                val pageIdx = findPageIndex(handle, absX, absY)
                val pr = handle.pageRects[pageIdx]
                if (pr.width <= 0 || pr.height <= 0) return@mapNotNull null
                SelectionRect(
                    xFrac = ((absX - pr.x).toFloat() / pr.width).coerceIn(0f, 1f),
                    yFrac = ((absY - pr.y).toFloat() / pr.height).coerceIn(0f, 1f),
                    widthFrac = (w.toFloat() / pr.width).coerceIn(0f, 1f),
                    heightFrac = (h.toFloat() / pr.height).coerceIn(0f, 1f),
                    partIndex = pageIdx
                )
            }
            _selection.value = rects
        } else {
            // 단일 part — 기존 정규화
            val dw = handle.lastDocWidthTwips.get()
            val dh = handle.lastDocHeightTwips.get()
            if (dw <= 0 || dh <= 0) return
            val rects = payload.split(';').mapNotNull { rectStr ->
                val nums = rectStr.split(',').mapNotNull { it.trim().toIntOrNull() }
                if (nums.size < 4) null else SelectionRect(
                    xFrac = (nums[0].toFloat() / dw).coerceIn(0f, 1f),
                    yFrac = (nums[1].toFloat() / dh).coerceIn(0f, 1f),
                    widthFrac = (nums[2].toFloat() / dw).coerceIn(0f, 1f),
                    heightFrac = (nums[3].toFloat() / dh).coerceIn(0f, 1f),
                    partIndex = handle.lastEditPart.get()
                )
            }
            _selection.value = rects
        }
    }

    /**
     * INVALIDATE_VISIBLE_CURSOR payload 형식: "x, y, width, height" (twips).
     * 페이지 분할된 DOCX 면 커서가 속한 페이지를 찾아 그 페이지 기준으로 정규화.
     */
    private fun updateCursorFromPayload(handle: Handle, payload: String?) {
        if (payload.isNullOrBlank()) return
        val nums = payload.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (nums.size < 4) return
        val cx = nums[0]; val cy = nums[1]
        val cw = nums[2]; val ch = nums[3]
        if (handle.pageRects != null) {
            val pageIdx = findPageIndex(handle, cx, cy)
            val pr = handle.pageRects[pageIdx]
            if (pr.width <= 0 || pr.height <= 0) return
            _cursor.value = CursorState(
                xFrac = ((cx - pr.x).toFloat() / pr.width).coerceIn(0f, 1f),
                yFrac = ((cy - pr.y).toFloat() / pr.height).coerceIn(0f, 1f),
                widthFrac = (cw.coerceAtLeast(20).toFloat() / pr.width).coerceAtLeast(0.001f),
                heightFrac = (ch.toFloat() / pr.height).coerceAtLeast(0.002f),
                partIndex = pageIdx,
                visible = true
            )
        } else {
            val dw = handle.lastDocWidthTwips.get()
            val dh = handle.lastDocHeightTwips.get()
            if (dw <= 0 || dh <= 0) return
            _cursor.value = CursorState(
                xFrac = (cx.toFloat() / dw).coerceIn(0f, 1f),
                yFrac = (cy.toFloat() / dh).coerceIn(0f, 1f),
                widthFrac = (cw.coerceAtLeast(20).toFloat() / dw).coerceAtLeast(0.001f),
                heightFrac = (ch.toFloat() / dh).coerceAtLeast(0.002f),
                partIndex = handle.lastEditPart.get(),
                visible = true
            )
        }
    }

    /** 모든 캐시된 비트맵 무효화. tile invalidation 시 VM 이 호출. */
    suspend fun invalidateAll(handle: DocumentHandle) {
        val h = handle as Handle
        withContext(Dispatchers.IO) {
            h.mutex.withLock {
                h.bitmapCache.evictAll()
                // 편집으로 텍스트 변경 → 페이지별 텍스트 캐시도 무효화
                h.cachedPageTexts = null
            }
        }
    }

    /**
     * 화면 좌표 (xPx, yPx) → LOK twips 변환 후 mouse down+up 이벤트 발생.
     * 편집 모드에서 사용자가 화면 탭한 위치에 LOK 커서를 놓을 때 사용.
     */
    suspend fun postMouseTap(
        handle: DocumentHandle,
        pageIndex: Int,
        xPx: Int,
        yPx: Int,
        widthPx: Int,
        heightPx: Int
    ): Boolean {
        if (widthPx <= 0 || heightPx <= 0) return false
        val h = handle as Handle
        return withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching {
                    val xTwips: Int
                    val yTwips: Int
                    if (h.pageRects != null) {
                        // 페이지 분할 — 절대 좌표 = 페이지 offset + 페이지 내 비율
                        val rect = h.pageRects[pageIndex.coerceIn(0, h.pageRects.size - 1)]
                        xTwips = rect.x + (xPx.toLong() * rect.width / widthPx).toInt()
                        yTwips = rect.y + (yPx.toLong() * rect.height / heightPx).toInt()
                        h.lastEditPart.set(pageIndex.coerceIn(0, h.pageRects.size - 1))
                        // 전체 doc 크기는 paintTile 들이 갱신하므로 여기서 추가 갱신 안 함
                    } else {
                        if (h.partCount > 1) {
                            h.document.setPart(pageIndex.coerceIn(0, h.partCount - 1))
                        }
                        h.lastEditPart.set(pageIndex.coerceIn(0, (h.partCount - 1).coerceAtLeast(0)))
                        val docW = h.document.documentWidth.toInt()
                        val docH = h.document.documentHeight.toInt()
                        h.lastDocWidthTwips.set(docW.toLong())
                        h.lastDocHeightTwips.set(docH.toLong())
                        if (docW <= 0 || docH <= 0) return@runCatching false
                        xTwips = (xPx.toLong() * docW / widthPx).toInt()
                        yTwips = (yPx.toLong() * docH / heightPx).toInt()
                    }
                    h.document.postMouseEvent(
                        Document.MOUSE_EVENT_BUTTON_DOWN,
                        xTwips, yTwips, 1,
                        Document.MOUSE_BUTTON_LEFT, 0
                    )
                    h.document.postMouseEvent(
                        Document.MOUSE_EVENT_BUTTON_UP,
                        xTwips, yTwips, 1,
                        Document.MOUSE_BUTTON_LEFT, 0
                    )
                    true
                }.onFailure { Timber.w(it, "postMouseTap 실패") }.getOrDefault(false)
            }
        }
    }

    /**
     * 문서에서 사용 가능한 글꼴 목록 조회.
     * LOK `getCommandValues(".uno:CharFontName")` 의 JSON 을 파싱.
     * 실패 시 빈 리스트 → UI 가 기본 목록으로 폴백.
     */
    suspend fun getAvailableFonts(handle: DocumentHandle): List<String> {
        val h = handle as Handle
        return withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching {
                    val json = h.document.getCommandValues(".uno:CharFontName")
                        ?: return@runCatching emptyList()
                    val obj = org.json.JSONObject(json)
                    val values = obj.optJSONObject("commandValues")
                        ?: return@runCatching emptyList()
                    val names = ArrayList<String>(values.length())
                    val keys = values.keys()
                    while (keys.hasNext()) names.add(keys.next())
                    names.sorted()
                }.getOrElse {
                    Timber.w(it, "getAvailableFonts 실패")
                    emptyList()
                }
            }
        }
    }

    /**
     * 현재 선택된 텍스트 추출 (Android clipboard 로 복사할 때 사용).
     * 선택이 없으면 null.
     */
    suspend fun getSelectedText(handle: DocumentHandle): String? {
        val h = handle as Handle
        return withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching {
                    h.document.getTextSelection("text/plain;charset=utf-8")
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }
        }
    }

    /**
     * Android clipboard 에서 받은 텍스트를 LOK 에 붙여넣기.
     */
    suspend fun pasteText(handle: DocumentHandle, text: String): Boolean {
        val h = handle as Handle
        return withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching {
                    h.document.paste("text/plain;charset=utf-8", text)
                }.onFailure { Timber.w(it, "paste 실패") }.getOrDefault(false)
            }
        }
    }

    /**
     * `.uno:Bold` 같은 LOK UNO 명령 실행. 서식 도구바에서 사용.
     *
     * @param arguments JSON 형식 인자 (예: `{"FontHeight.Height":{"type":"float","value":"14"}}`).
     *   null 이면 단순 토글 명령 (Bold/Italic 등).
     */
    suspend fun postUnoCommand(
        handle: DocumentHandle,
        command: String,
        arguments: String? = null
    ): Boolean {
        val h = handle as Handle
        return withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching {
                    h.document.postUnoCommand(command, arguments, false)
                    true
                }.onFailure { Timber.w(it, "postUnoCommand 실패: $command args=$arguments") }
                    .getOrDefault(false)
            }
        }
    }

    /**
     * LOK 에 키 이벤트 전송 (press + release 쌍).
     * @param charCode 유니코드 코드 포인트 (ASCII/UTF-16 단위)
     * @param lokKeyCode LOK 특수키 코드 (Backspace=1283, Return=1280 등). 0=일반 문자.
     */
    suspend fun postKey(
        handle: DocumentHandle,
        charCode: Int,
        lokKeyCode: Int = 0
    ): Boolean {
        val h = handle as Handle
        return withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching {
                    h.document.postKeyEvent(Document.KEY_EVENT_PRESS, charCode, lokKeyCode)
                    h.document.postKeyEvent(Document.KEY_EVENT_RELEASE, charCode, lokKeyCode)
                    true
                }.onFailure { Timber.w(it, "postKey 실패 char=$charCode lok=$lokKeyCode") }
                    .getOrDefault(false)
            }
        }
    }

    /**
     * 현재 열린 문서를 원본 임시 파일에 덮어쓰기.
     * LOK 가 saveAs 만 제공 → tempFile 경로에 원본 포맷으로 저장 → UI 가 SAF 로 복사.
     * @param lokFormat 원본 포맷 단축명. Office 편집 저장은 보통 원본 포맷 그대로.
     */
    suspend fun saveCurrentDocument(
        handle: DocumentHandle,
        outFile: File,
        lokFormat: String
    ): Boolean = saveAs(handle, outFile, lokFormat)

    override suspend fun pageCount(handle: DocumentHandle): Int {
        val h = handle as Handle
        return h.pageRects?.size ?: h.partCount
    }

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize {
        val h = handle as Handle
        // 페이지 분할된 DOCX: rect 에서 직접
        h.pageRects?.let { rects ->
            val rect = rects[index.coerceIn(0, rects.size - 1)]
            return PageSize(
                widthPt = rect.width.toFloat() / TWIPS_PER_POINT,
                heightPt = rect.height.toFloat() / TWIPS_PER_POINT
            )
        }
        return h.mutex.withLock {
            runCatching {
                if (h.partCount > 1) h.document.setPart(index.coerceIn(0, h.partCount - 1))
                PageSize(
                    widthPt = h.document.documentWidth.toFloat() / TWIPS_PER_POINT,
                    heightPt = h.document.documentHeight.toFloat() / TWIPS_PER_POINT
                )
            }.getOrElse {
                Timber.w(it, "pageSize 실패 — 기본값 반환")
                PageSize(595f, 842f)  // A4 기본
            }
        }
    }

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = withContext(Dispatchers.IO) {
        val h = handle as Handle
        val widthPx = targetWidthPx.coerceAtLeast(MIN_WIDTH_PX)
        val cacheKey = CacheKey(index, widthPx)

        // 캐시 hit
        h.bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) {
                return@withContext RenderedPage(
                    index = index,
                    widthPx = cached.width,
                    heightPx = cached.height,
                    bitmap = cached
                )
            }
            h.bitmapCache.remove(cacheKey)  // 회수된 비트맵 청소
        }

        h.mutex.withLock {
            // 동시에 같은 페이지 요청 시 다른 쪽이 캐시 채웠을 수 있음
            h.bitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) {
                    return@withLock RenderedPage(index, cached.width, cached.height, cached)
                }
            }

            // 타일 위치/크기 결정 — 페이지 분할 모드면 그 페이지의 rect, 아니면 전체 문서
            val tileX: Int
            val tileY: Int
            val tileW: Int
            val tileH: Int
            if (h.pageRects != null) {
                val rect = h.pageRects[index.coerceIn(0, h.pageRects.size - 1)]
                tileX = rect.x; tileY = rect.y
                tileW = rect.width; tileH = rect.height
                // 페이지 분할 — 전체 doc 크기는 cursor/selection 전역 정규화에 안 씀
                h.lastDocWidthTwips.set(tileW.toLong())
                h.lastDocHeightTwips.set(tileH.toLong())
            } else {
                if (h.partCount > 1) {
                    runCatching { h.document.setPart(index.coerceIn(0, h.partCount - 1)) }
                        .onFailure { Timber.w(it, "setPart($index) 실패") }
                }
                val dw = runCatching { h.document.documentWidth }.getOrDefault(0L).toInt()
                val dh = runCatching { h.document.documentHeight }.getOrDefault(0L).toInt()
                if (dw <= 0 || dh <= 0) {
                    throw DocumentError.Corrupted(
                        IllegalStateException("LOK 문서 크기 0: w=$dw h=$dh")
                    )
                }
                tileX = 0; tileY = 0; tileW = dw; tileH = dh
                h.lastDocWidthTwips.set(dw.toLong())
                h.lastDocHeightTwips.set(dh.toLong())
            }

            // 비율 유지하며 MAX_HEIGHT_PX 이내로 맞춤.
            // raw heightPx 가 8192 초과면 widthPx 를 비례 축소 → 잘림 대신 해상도 낮춤.
            val rawHeightPx = (widthPx.toLong() * tileH / tileW).toInt().coerceAtLeast(1)
            val (renderWidthPx, heightPx) = if (rawHeightPx > MAX_HEIGHT_PX) {
                val scale = MAX_HEIGHT_PX.toFloat() / rawHeightPx
                val newWidth = (widthPx * scale).toInt().coerceAtLeast(MIN_WIDTH_PX)
                Timber.d("페이지 $index 크기 초과 — widthPx $widthPx→$newWidth, heightPx $rawHeightPx→${MAX_HEIGHT_PX}")
                Pair(newWidth, MAX_HEIGHT_PX)
            } else {
                Pair(widthPx, rawHeightPx)
            }

            // 비트맵을 위해 DirectBuffer 할당
            val bufferSize = renderWidthPx * heightPx * 4  // ARGB 8888
            val buffer: ByteBuffer = try {
                DirectBufferAllocator.allocate(bufferSize)
            } catch (oom: OutOfMemoryError) {
                Timber.e(oom, "DirectBuffer 할당 실패 — $bufferSize bytes")
                throw DocumentError.Unknown(oom)
            }

            try {
                // LOK paintTile: ARGB DirectBuffer 에 그림.
                // 페이지 분할 모드: 해당 페이지 rect 만 슬라이스, 단일 모드: 전체 문서.
                runCatching {
                    h.document.paintTile(
                        buffer,
                        renderWidthPx,
                        heightPx,
                        tileX,
                        tileY,
                        tileW,
                        tileH
                    )
                }.getOrElse {
                    Timber.e(it, "paintTile($index) 실패")
                    throw DocumentError.Corrupted(it)
                }

                val bitmap = Bitmap.createBitmap(renderWidthPx, heightPx, Bitmap.Config.ARGB_8888)
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)

                // 캐시 저장 — 평가 후 LRU 가 알아서 evict
                h.bitmapCache.put(cacheKey, bitmap)

                RenderedPage(
                    index = index,
                    widthPx = renderWidthPx,
                    heightPx = heightPx,
                    bitmap = bitmap
                )
            } finally {
                runCatching { DirectBufferAllocator.free(buffer) }
            }
        }
    }

    override suspend fun getPageText(handle: DocumentHandle, index: Int): String? {
        val h = handle as Handle
        return withContext(Dispatchers.IO) {
            // 페이지 분할된 DOCX: 전체 텍스트를 한 번만 가져와 페이지 수에 비례해 분할
            if (h.pageRects != null) {
                val pages = h.pageRects.size
                val cachedSplit = h.cachedPageTexts
                if (cachedSplit != null) {
                    return@withContext cachedSplit.getOrNull(index.coerceIn(0, pages - 1))
                }
                val full = h.mutex.withLock { extractFullText(h) } ?: return@withContext null
                val split = splitTextProportionally(full, pages)
                h.cachedPageTexts = split
                return@withContext split.getOrNull(index.coerceIn(0, pages - 1))
            }
            // multi-part: setPart 후 SelectAll
            h.mutex.withLock {
                runCatching {
                    if (h.partCount > 1) h.document.setPart(index.coerceIn(0, h.partCount - 1))
                    h.document.postUnoCommand(".uno:SelectAll", null, false)
                    val text = h.document.getTextSelection("text/plain;charset=utf-8")
                    h.document.resetSelection()
                    text?.takeIf { it.isNotBlank() }
                }.getOrElse {
                    Timber.w(it, "getPageText($index) 실패")
                    null
                }
            }
        }
    }

    private fun extractFullText(h: Handle): String? = runCatching {
        h.document.postUnoCommand(".uno:SelectAll", null, false)
        val t = h.document.getTextSelection("text/plain;charset=utf-8")
        h.document.resetSelection()
        t?.takeIf { it.isNotBlank() }
    }.onFailure { Timber.w(it, "extractFullText 실패") }.getOrNull()

    /**
     * 전체 텍스트를 [pages] 개로 균등 분할. 각 경계는 가장 가까운 newline 으로 스냅 (±100 자 내).
     * 페이지 별 글자 수가 정확하진 않지만 검색 매치를 비슷한 페이지로 매핑하는 데 충분.
     */
    private fun splitTextProportionally(fullText: String, pages: Int): List<String> {
        if (pages <= 1 || fullText.isEmpty()) return listOf(fullText)
        val per = fullText.length / pages
        val result = ArrayList<String>(pages)
        var offset = 0
        for (i in 0 until pages - 1) {
            val target = (i + 1) * per
            val snapped = snapToNewline(fullText, target).coerceIn(offset, fullText.length)
            result.add(fullText.substring(offset, snapped))
            offset = snapped
        }
        result.add(fullText.substring(offset))
        return result
    }

    private fun snapToNewline(text: String, position: Int): Int {
        if (position >= text.length) return text.length
        if (position <= 0) return 0
        val radius = 100
        val start = (position - radius).coerceAtLeast(0)
        val end = (position + radius).coerceAtMost(text.length)
        var bestDist = Int.MAX_VALUE
        var bestPos = position
        for (i in start until end) {
            if (text[i] == '\n') {
                val d = kotlin.math.abs(i + 1 - position)
                if (d < bestDist) { bestDist = d; bestPos = i + 1 }
            }
        }
        return bestPos
    }

    /**
     * 현재 열린 문서를 다른 포맷으로 임시 파일에 저장. UI 가 결과 파일을 SAF URI 로 복사.
     *
     * @param lokFormat LOK 가 인식하는 포맷 단축명: "pdf", "docx", "odt", "pptx", "odp", "xlsx", "ods"
     * @return 저장 성공 시 true. LOK 가 result 를 반환하지 않으므로 파일 존재/크기로 판정.
     */
    suspend fun saveAs(handle: DocumentHandle, outFile: File, lokFormat: String): Boolean {
        val h = handle as Handle
        return withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching {
                    val url = "file://${outFile.absolutePath}"
                    Timber.i("LOK saveAs: $lokFormat → ${outFile.absolutePath}")
                    h.document.saveAs(url, lokFormat, "")
                    val ok = outFile.exists() && outFile.length() > 0
                    if (!ok) Timber.w("LOK saveAs: 결과 파일 없음/빈 파일")
                    ok
                }.getOrElse {
                    Timber.e(it, "LOK saveAs 실패: $lokFormat")
                    false
                }
            }
        }
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        withContext(Dispatchers.IO) {
            h.mutex.withLock {
                // 캐시된 비트맵 해제
                h.bitmapCache.evictAll()
                runCatching { h.document.destroy() }
                    .onFailure { Timber.w(it, "Document.destroy 실패") }
                runCatching { h.tempFile.delete() }
            }
        }
    }

    private fun copyToCache(uri: Uri): File {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
            ?: "bin"
        val temp = File.createTempFile("lok_", ".$ext", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "openInputStream returned null for $uri" }
            temp.outputStream().use { input.copyTo(it) }
        }
        return temp
    }

    private fun classifyOpenError(errorMsg: String, hadPassword: Boolean): DocumentError {
        val lower = errorMsg.lowercase()
        return when {
            "password" in lower || "encrypted" in lower ->
                DocumentError.PasswordProtected(wrongPassword = hadPassword)
            "unsupported" in lower || "version" in lower ->
                DocumentError.UnsupportedVariant("LOK 미지원 변형", RuntimeException(errorMsg))
            else -> DocumentError.Corrupted(RuntimeException("LOK documentLoad 실패: $errorMsg"))
        }
    }

    private companion object {
        const val TWIPS_PER_POINT = 20f  // 1 pt = 20 twips
        const val MIN_WIDTH_PX = 100
        const val MAX_HEIGHT_PX = 8192  // 안전 한계
        // 비트맵 캐시 ~30MB (페이지 ARGB 1080x1512 ≈ 6.5MB → ~4-5 페이지)
        const val BITMAP_CACHE_SIZE_BYTES = 30 * 1024 * 1024
    }
}

/**
 * LOK 라우팅 대상 포맷 — RendererRegistryImpl 이 활성화 조건과 함께 사용.
 *
 * HWP 제외 이유: 우리 enum 은 .hwp 와 .hwpx 를 한 묶음으로 본다.
 *   - LO 는 HWP 5.0 만 (hwpfilter 모듈) 지원, HWPX 미지원.
 *   - .hwpx 가 LOK 로 라우팅되면 깨짐.
 *   - 안전하게 기존 hwplib/hwpxlib 경로 유지.
 */
val LOK_SUPPORTED_FORMATS: Set<DocumentFormat> = setOf(
    DocumentFormat.DOCX,
    DocumentFormat.PPTX,
    DocumentFormat.XLSX
)
