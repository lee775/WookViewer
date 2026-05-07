package com.wook.viewer.render.hwp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HwpPaginatorTest {

    @Test fun `빈 입력은 안내 페이지 한 장`() {
        val pages = HwpPaginator.paginate("")
        assertEquals(1, pages.size)
        assertTrue(pages[0].text.contains("빈 문서"))
    }

    @Test fun `짧은 단일 단락은 한 페이지`() {
        val pages = HwpPaginator.paginate("안녕하세요. 짧은 문서입니다.")
        assertEquals(1, pages.size)
        assertTrue(pages[0].text.contains("안녕하세요"))
    }

    @Test fun `한 페이지 한도 초과 시 분리`() {
        val para = "한글텍스트 ".repeat(200)  // ≈ 1200 chars
        val text = (1..3).joinToString("\n") { para }  // ≈ 3600+ chars
        val pages = HwpPaginator.paginate(text)
        assertTrue("페이지가 여러 개여야 함: ${pages.size}", pages.size >= 2)
        // 페이지 길이 합 ≈ 원문 길이
        val total = pages.sumOf { it.text.length }
        assertTrue(total >= text.length - pages.size * 2)
    }

    @Test fun `한 단락이 한도보다 길면 강제 분할`() {
        val huge = "X".repeat(HwpPaginator.APPROX_CHARS_PER_PAGE * 3)
        val pages = HwpPaginator.paginate(huge)
        assertTrue("강제 분할 페이지 ≥ 3: ${pages.size}", pages.size >= 3)
        // 첫 페이지는 정확히 한도 길이
        assertEquals(HwpPaginator.APPROX_CHARS_PER_PAGE, pages[0].text.length)
    }

    @Test fun `여러 단락이 한 페이지 안에 들어가는 경우`() {
        val text = (1..5).joinToString("\n") { "단락$it" }
        val pages = HwpPaginator.paginate(text)
        assertEquals(1, pages.size)
        // 각 단락이 결과에 포함
        (1..5).forEach { assertTrue(pages[0].text.contains("단락$it")) }
    }
}
