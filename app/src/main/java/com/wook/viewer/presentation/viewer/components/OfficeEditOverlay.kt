package com.wook.viewer.presentation.viewer.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * LOK 특수 키 코드 — `org/libreoffice/vcl/keycodes.hxx` 와 동일.
 */
internal object LokKey {
    const val RETURN = 1280
    const val ESCAPE = 1281
    const val TAB = 1282
    const val BACKSPACE = 1283
    const val DELETE = 1286
}

/**
 * Office 편집 모드 전용 입력 오버레이.
 *
 * 동작:
 *  - 화면 탭 → [onTap] (LOK 커서 위치)
 *  - 화면 길게 누름 → [onLongPress] (단어 선택)
 *  - IME 입력 → [onChar] / [onSpecialKey]
 *  - LOK 가 알려준 커서 + 선택 영역 → 시각적 표시
 *
 * IME 처리 — composition-aware:
 *  - BasicTextField 의 [TextFieldValue.composition] 으로 IME 조합 중 구간 식별
 *  - "확정된(committed) 텍스트" 길이 변화만 LOK 로 전송 → 한글 자모 중간 단계 전송 안 함
 *  - composition 이 없는 안정 상태가 되면 버퍼 리셋
 */
@Composable
fun OfficeEditOverlay(
    pageIndex: Int,
    onTap: (xPx: Int, yPx: Int, widthPx: Int, heightPx: Int) -> Unit,
    onLongPress: (xPx: Int, yPx: Int, widthPx: Int, heightPx: Int) -> Unit,
    onChar: (codePoint: Int) -> Unit,
    onSpecialKey: (lokKeyCode: Int) -> Unit,
    cursorXFrac: Float? = null,
    cursorYFrac: Float? = null,
    cursorWidthFrac: Float? = null,
    cursorHeightFrac: Float? = null,
    /** 페이지에 속한 선택 영역들 — 정규화 비율 (x,y,w,h). */
    selectionRects: List<FloatArray> = emptyList(),
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    // 확정된 텍스트(composition 제외) 길이 — diff 기준
    var committedLen by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pageIndex) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onTap = { offset ->
                        if (size.width > 0 && size.height > 0) {
                            onTap(
                                offset.x.toInt().coerceIn(0, size.width - 1),
                                offset.y.toInt().coerceIn(0, size.height - 1),
                                size.width,
                                size.height
                            )
                        }
                        runCatching { focusRequester.requestFocus() }
                    },
                    onLongPress = { offset ->
                        if (size.width > 0 && size.height > 0) {
                            onLongPress(
                                offset.x.toInt().coerceIn(0, size.width - 1),
                                offset.y.toInt().coerceIn(0, size.height - 1),
                                size.width,
                                size.height
                            )
                        }
                        runCatching { focusRequester.requestFocus() }
                    }
                )
            }
    ) {
        // IME 키 캡처 전용 — 1dp 투명 BasicTextField. composition 추적해 한글 지원.
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val text = newValue.text
                val comp = newValue.composition
                val committed = if (comp != null) {
                    text.removeRange(comp.min, comp.max)
                } else {
                    text
                }
                val newCommittedLen = committed.length

                when {
                    newCommittedLen > committedLen -> {
                        // 확정된 새 글자 → LOK 로 codePoint 전송
                        // 서로게이트 페어(이모지 등) 처리: codePointAt 으로 안전하게
                        var i = committedLen
                        while (i < committed.length) {
                            val cp = committed.codePointAt(i)
                            onChar(cp)
                            i += Character.charCount(cp)
                        }
                    }
                    newCommittedLen < committedLen -> {
                        // 확정 글자 줄어듦 → 백스페이스로 동기화
                        repeat(committedLen - newCommittedLen) {
                            onSpecialKey(LokKey.BACKSPACE)
                        }
                    }
                }

                if (comp == null) {
                    // 안정 상태 — 필드 비우고 다시 시작 (한글 IME 가 다음 입력을 새 조합으로 처리)
                    fieldValue = TextFieldValue("")
                    committedLen = 0
                } else {
                    // 조합 중 — 필드 상태 유지하고 IME 가 계속 조합하도록
                    fieldValue = newValue
                    committedLen = newCommittedLen
                }
            },
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                autoCorrect = false,
                imeAction = ImeAction.Default
            ),
            keyboardActions = KeyboardActions(
                onAny = { onSpecialKey(LokKey.RETURN) }
            ),
            singleLine = false,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent)
        )

        // 선택 영역 — 반투명 파란 박스
        if (selectionRects.isNotEmpty() && size.width > 0 && size.height > 0) {
            selectionRects.forEach { rect ->
                if (rect.size >= 4) {
                    SelectionRectBox(
                        xPx = (rect[0] * size.width).toInt(),
                        yPx = (rect[1] * size.height).toInt(),
                        widthPx = (rect[2] * size.width).toInt().coerceAtLeast(2),
                        heightPx = (rect[3] * size.height).toInt().coerceAtLeast(2)
                    )
                }
            }
        }

        // LOK 커서 시각화 — 깜빡이는 얇은 세로 막대
        if (cursorXFrac != null && cursorYFrac != null && cursorHeightFrac != null &&
            size.width > 0 && size.height > 0
        ) {
            BlinkingCursor(
                xPx = (cursorXFrac * size.width).toInt(),
                yPx = (cursorYFrac * size.height).toInt(),
                heightPx = (cursorHeightFrac * size.height).toInt().coerceAtLeast(8),
                widthPx = ((cursorWidthFrac ?: 0f) * size.width).toInt().coerceAtLeast(2)
            )
        }
    }
}

@Composable
private fun SelectionRectBox(xPx: Int, yPx: Int, widthPx: Int, heightPx: Int) {
    val density = LocalDensity.current
    val xDp = with(density) { xPx.toDp() }
    val yDp = with(density) { yPx.toDp() }
    val wDp = with(density) { widthPx.toDp() }
    val hDp = with(density) { heightPx.toDp() }
    Box(
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .size(width = wDp, height = hDp)
            .background(Color(0x551976D2))  // 반투명 블루
    )
}

@Composable
private fun BlinkingCursor(xPx: Int, yPx: Int, widthPx: Int, heightPx: Int) {
    val density = LocalDensity.current
    val xDp = with(density) { xPx.toDp() }
    val yDp = with(density) { yPx.toDp() }
    val wDp = with(density) { widthPx.toDp() }
    val hDp = with(density) { heightPx.toDp() }

    // 0.53 초 주기로 깜빡임
    val transition = rememberInfiniteTransition(label = "lokCursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )
    Box(
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .size(width = wDp, height = hDp)
            .background(Color(0xFF1976D2).copy(alpha = alpha))
    )
}
