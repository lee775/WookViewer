package com.wook.viewer.render.text

/**
 * 포맷에 무관한 "텍스트 1 페이지" 단위.
 * HWP/DOCX 등 TEXT_ONLY 충실도 포맷이 공통으로 사용.
 */
internal data class TextPage(val text: String)
