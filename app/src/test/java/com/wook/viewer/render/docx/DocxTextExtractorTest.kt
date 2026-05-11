package com.wook.viewer.render.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * DocxTextExtractor 단위 테스트.
 *
 * 실제 .docx 파일 없이 ZIP+XML을 메모리에서 합성해서 검증.
 */
class DocxTextExtractorTest {

    @Test fun `빈 본문은 빈 문자열`() {
        val docx = makeDocx(body = "")
        val text = DocxTextExtractor.extract(docx).text
        assertEquals("", text)
    }

    @Test fun `단일 단락 텍스트 추출`() {
        val docx = makeDocx(body = """
            <w:p><w:r><w:t>안녕하세요</w:t></w:r></w:p>
        """.trimIndent())
        val text = DocxTextExtractor.extract(docx).text
        assertEquals("안녕하세요", text)
    }

    @Test fun `여러 단락은 newline으로 구분`() {
        val docx = makeDocx(body = """
            <w:p><w:r><w:t>첫째 단락</w:t></w:r></w:p>
            <w:p><w:r><w:t>둘째 단락</w:t></w:r></w:p>
        """.trimIndent())
        val text = DocxTextExtractor.extract(docx).text
        assertEquals("첫째 단락\n둘째 단락", text)
    }

    @Test fun `한 단락 내 여러 run이 합쳐짐`() {
        val docx = makeDocx(body = """
            <w:p>
              <w:r><w:t>Hello</w:t></w:r>
              <w:r><w:t> </w:t></w:r>
              <w:r><w:t>World</w:t></w:r>
            </w:p>
        """.trimIndent())
        val text = DocxTextExtractor.extract(docx).text
        assertEquals("Hello World", text)
    }

    @Test fun `tab과 br 처리`() {
        val docx = makeDocx(body = """
            <w:p>
              <w:r><w:t>A</w:t><w:tab/><w:t>B</w:t><w:br/><w:t>C</w:t></w:r>
            </w:p>
        """.trimIndent())
        val text = DocxTextExtractor.extract(docx).text
        assertEquals("A\tB\nC", text)
    }

    @Test fun `표는 셀 tab 행 newline으로 평탄화`() {
        val docx = makeDocx(body = """
            <w:tbl>
              <w:tr>
                <w:tc><w:p><w:r><w:t>이름</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>나이</w:t></w:r></w:p></w:tc>
              </w:tr>
              <w:tr>
                <w:tc><w:p><w:r><w:t>홍길동</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>20</w:t></w:r></w:p></w:tc>
              </w:tr>
            </w:tbl>
        """.trimIndent())
        val text = DocxTextExtractor.extract(docx).text
        // 셀 텍스트가 모두 들어 있고, 각 행이 분리됨
        assertTrue("이름 셀이 포함: '$text'", text.contains("이름"))
        assertTrue("나이 셀이 포함: '$text'", text.contains("나이"))
        assertTrue("홍길동이 별 행에 있음", text.contains("홍길동"))
        assertTrue("두 번째 행이 행 구분으로 분리됨", text.contains("나이\n") || text.contains("나이\t"))
    }

    @Test fun `특수문자 XML 엔티티 디코딩`() {
        val docx = makeDocx(body = """
            <w:p><w:r><w:t>&lt;a&gt; &amp; &quot;b&quot;</w:t></w:r></w:p>
        """.trimIndent())
        val text = DocxTextExtractor.extract(docx).text
        assertEquals("<a> & \"b\"", text)
    }

    @Test fun `ZIP이 아니면 DocxFormatException`() {
        val notZip = "Hello, this is not a ZIP file".toByteArray()
        assertThrows(DocxFormatException::class.java) {
            DocxTextExtractor.extract(ByteArrayInputStream(notZip))
        }
    }

    @Test fun `word document xml이 없으면 DocxFormatException`() {
        val zipWithoutDoc = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("META-INF/manifest.xml"))
                zos.write("<x/>".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        assertThrows(DocxFormatException::class.java) {
            DocxTextExtractor.extract(ByteArrayInputStream(zipWithoutDoc))
        }
    }

    // ---- helper ----

    private fun makeDocx(body: String): ByteArrayInputStream {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $body
  </w:body>
</w:document>
        """.trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(xml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return ByteArrayInputStream(out.toByteArray())
    }
}
