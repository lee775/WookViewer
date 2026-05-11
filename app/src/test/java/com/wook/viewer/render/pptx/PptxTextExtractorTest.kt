package com.wook.viewer.render.pptx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * PptxTextExtractor 단위 테스트.
 *
 * 메모리에서 ZIP+XML을 합성해 검증 — 실제 .pptx 파일 없이.
 */
class PptxTextExtractorTest {

    @Test fun `슬라이드 없으면 PptxFormatException`() {
        val zipNoSlides = makePptx(emptyMap())
        assertThrows(PptxFormatException::class.java) {
            PptxTextExtractor.extract(zipNoSlides)
        }
    }

    @Test fun `단일 슬라이드 텍스트 추출`() {
        val pptx = makePptx(mapOf(
            1 to slideXml("<a:p><a:r><a:t>Hello</a:t></a:r></a:p>")
        ))
        val slides = PptxTextExtractor.extract(pptx)
        assertEquals(1, slides.size)
        assertEquals("Hello", slides[0].text)
    }

    @Test fun `슬라이드 자연수 정렬 - slide10이 slide2 뒤에 위치`() {
        val slidesIn = (1..11).associateWith { i ->
            slideXml("<a:p><a:r><a:t>Slide$i</a:t></a:r></a:p>")
        }
        val pptx = makePptx(slidesIn)
        val texts = PptxTextExtractor.extract(pptx)

        assertEquals(11, texts.size)
        // 알파벳 정렬이면 [Slide1, Slide10, Slide11, Slide2, …]가 되어야 하는데
        // 자연수 정렬은 [Slide1, Slide2, …, Slide10, Slide11]
        (1..11).forEachIndexed { idx, num ->
            assertEquals("순서 $idx 슬라이드", "Slide$num", texts[idx].text)
        }
    }

    @Test fun `슬라이드 내 다중 텍스트박스(sp)는 줄바꿈으로 구분`() {
        val pptx = makePptx(mapOf(
            1 to slideXml("""
                <p:sp>
                  <p:txBody><a:p><a:r><a:t>제목</a:t></a:r></a:p></p:txBody>
                </p:sp>
                <p:sp>
                  <p:txBody><a:p><a:r><a:t>본문</a:t></a:r></a:p></p:txBody>
                </p:sp>
            """.trimIndent())
        ))
        val texts = PptxTextExtractor.extract(pptx).map { it.text }
        assertEquals(1, texts.size)
        assertTrue("제목 포함: '${texts[0]}'", texts[0].contains("제목"))
        assertTrue("본문 포함", texts[0].contains("본문"))
        // 텍스트박스 사이에 줄바꿈
        assertTrue("줄바꿈 포함", texts[0].contains("제목") &&
                texts[0].contains("\n") && texts[0].contains("본문"))
    }

    @Test fun `슬라이드 내 표는 셀 tab 행 newline으로`() {
        val pptx = makePptx(mapOf(
            1 to slideXml("""
                <a:tbl>
                  <a:tr>
                    <a:tc><a:txBody><a:p><a:r><a:t>A</a:t></a:r></a:p></a:txBody></a:tc>
                    <a:tc><a:txBody><a:p><a:r><a:t>B</a:t></a:r></a:p></a:txBody></a:tc>
                  </a:tr>
                  <a:tr>
                    <a:tc><a:txBody><a:p><a:r><a:t>C</a:t></a:r></a:p></a:txBody></a:tc>
                    <a:tc><a:txBody><a:p><a:r><a:t>D</a:t></a:r></a:p></a:txBody></a:tc>
                  </a:tr>
                </a:tbl>
            """.trimIndent())
        ))
        val texts = PptxTextExtractor.extract(pptx)
        val text = texts[0].text
        listOf("A", "B", "C", "D").forEach {
            assertTrue("$it 포함", text.contains(it))
        }
    }

    @Test fun `br 줄바꿈 처리`() {
        val pptx = makePptx(mapOf(
            1 to slideXml("<a:p><a:r><a:t>L1</a:t><a:br/><a:t>L2</a:t></a:r></a:p>")
        ))
        val texts = PptxTextExtractor.extract(pptx)
        assertEquals("L1\nL2", texts[0].text)
    }

    @Test fun `XML 엔티티 디코딩`() {
        val pptx = makePptx(mapOf(
            1 to slideXml("<a:p><a:r><a:t>&lt;tag&gt; &amp; &quot;q&quot;</a:t></a:r></a:p>")
        ))
        val texts = PptxTextExtractor.extract(pptx)
        assertEquals("<tag> & \"q\"", texts[0].text)
    }

    @Test fun `ZIP 아니면 PptxFormatException`() {
        val notZip = "not a zip".toByteArray()
        assertThrows(PptxFormatException::class.java) {
            PptxTextExtractor.extract(ByteArrayInputStream(notZip))
        }
    }

    @Test fun `slideMasters slideLayouts는 슬라이드로 카운트하지 않음`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            // 진짜 슬라이드
            zos.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
            zos.write(slideXml("<a:p><a:r><a:t>real</a:t></a:r></a:p>").toByteArray())
            zos.closeEntry()
            // 마스터/레이아웃 (필터되어야 함)
            zos.putNextEntry(ZipEntry("ppt/slideMasters/slideMaster1.xml"))
            zos.write(slideXml("<a:p><a:r><a:t>master</a:t></a:r></a:p>").toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("ppt/slideLayouts/slideLayout1.xml"))
            zos.write(slideXml("<a:p><a:r><a:t>layout</a:t></a:r></a:p>").toByteArray())
            zos.closeEntry()
        }
        val texts = PptxTextExtractor.extract(ByteArrayInputStream(out.toByteArray()))
        assertEquals(1, texts.size)
        assertEquals("real", texts[0].text)
    }

    // ---- helpers ----

    private fun slideXml(body: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
       xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <p:cSld>
    <p:spTree>
      $body
    </p:spTree>
  </p:cSld>
</p:sld>"""

    private fun makePptx(slides: Map<Int, String>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            slides.forEach { (num, xml) ->
                zos.putNextEntry(ZipEntry("ppt/slides/slide$num.xml"))
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }
}
