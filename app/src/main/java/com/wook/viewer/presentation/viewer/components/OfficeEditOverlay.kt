package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * LOK 특수 키 코드 — `org/libreoffice/vcl/keycodes.hxx` 와 동일.
 * postKeyEvent 의 keyCode 인자로 전달.
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
 *  - 화면 탭 → [onTap] 호출 (LOK 커서 위치 설정)
 *  - IME 키 입력 → [onChar] (일반 문자) 또는 [onSpecialKey] (Backspace/Enter)
 *
 * 한계 (v0.9.2 PoC):
 *  - `KeyboardType.Ascii` 로 IME 강제 → 한글 IME 미지원
 *  - 시각적 커서 표시 없음 (LOK 내부 커서만, UI 오버레이는 투명)
 *  - 텍스트 선택/긴 탭 미지원
 *
 * 구현 트릭 — sentinel 문자 ("X") 를 항상 BasicTextField 에 유지하고
 * onValueChange 에서 "X" 와 비교해 추가/삭제를 감지. 매 이벤트 후 sentinel 로 리셋.
 */
@Composable
fun OfficeEditOverlay(
    pageIndex: Int,
    onTap: (xPx: Int, yPx: Int, widthPx: Int, heightPx: Int) -> Unit,
    onChar: (codePoint: Int) -> Unit,
    onSpecialKey: (lokKeyCode: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val sentinel = remember { TextFieldValue("X", selection = TextRange(1)) }
    var fieldValue by remember { mutableStateOf(sentinel) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pageIndex) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(pageIndex) {
                detectTapGestures { offset ->
                    if (size.width > 0 && size.height > 0) {
                        onTap(
                            offset.x.toInt().coerceIn(0, size.width - 1),
                            offset.y.toInt().coerceIn(0, size.height - 1),
                            size.width,
                            size.height
                        )
                    }
                    runCatching { focusRequester.requestFocus() }
                }
            }
    ) {
        // 보이지 않는 BasicTextField — IME 키 캡처 전용. 1dp 크기 + 투명 텍스트.
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val text = newValue.text
                when {
                    // 문자가 추가됨 — sentinel "X" 이후의 글자들을 LOK 로 전송
                    text.length > 1 -> {
                        text.substring(1).forEach { ch -> onChar(ch.code) }
                    }
                    // sentinel 이 삭제됨 — backspace
                    text.isEmpty() -> {
                        onSpecialKey(LokKey.BACKSPACE)
                    }
                    // 길이 1 인데 "X" 가 아님 → 사용자가 sentinel 자체를 다른 글자로 교체
                    text != "X" -> {
                        onSpecialKey(LokKey.BACKSPACE)
                        onChar(text[0].code)
                    }
                }
                fieldValue = sentinel
            },
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
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
    }
}
