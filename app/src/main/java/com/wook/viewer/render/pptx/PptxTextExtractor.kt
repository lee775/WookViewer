package com.wook.viewer.render.pptx

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * PPTX (.pptx) 슬라이드별 텍스트 추출기.
 *
 * PPTX 구조:
 *   - .pptx = ZIP
 *   - 각 슬라이드는 `ppt/slides/slideN.xml` (DrawingML)
 *   - `<a:p>` 단락, `<a:r>` 런, `<a:t>` 텍스트
 *   - `<a:tbl>/<a:tr>/<a:tc>` 표
 *
 * 슬라이드 순서:
 *   - 파일명의 숫자로 자연수 정렬 (slide1 < slide2 < … < slide10 < slide11)
 *   - 한계: 사용자가 PowerPoint에서 슬라이드를 재배치한 경우 표시 순서와 다를 수 있음
 *     (정확한 순서는 ppt/_rels/presentation.xml.rels 파싱 필요 — v0.5 범위)
 *
 * 무시:
 *   - 슬라이드 마스터/레이아웃 (slideMasters, slideLayouts)
 *   - 발표자 노트 (notesSlide)
 *   - 이미지/도형/SmartArt
 *   - 애니메이션/전환
 *
 * 의존성: java.util.zip + javax.xml SAX (JVM 표준) — 외부 라이브러리 0개.
 */
internal object PptxTextExtractor {

    /** ppt/slides/slide{number}.xml 만 매칭 (slideLayout, slideMaster 등 제외). */
    private val SLIDE_PATH_REGEX = Regex("""ppt/slides/slide(\d+)\.xml""")

    /**
     * @return 슬라이드별 텍스트 (자연수 순). 1번 슬라이드부터 N번까지.
     * @throws PptxFormatException ZIP이 아니거나 슬라이드가 없음 (예: .ppt 구형 바이너리)
     */
    @Throws(Exception::class)
    fun extract(input: InputStream): List<String> {
        val slidesByNumber = mutableMapOf<Int, ByteArray>()
        try {
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val match = SLIDE_PATH_REGEX.matchEntire(entry.name)
                    if (match != null) {
                        val number = match.groupValues[1].toInt()
                        slidesByNumber[number] = zis.copyAllBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw PptxFormatException("Not a valid ZIP/PPTX file", e)
        }

        if (slidesByNumber.isEmpty()) {
            throw PptxFormatException("No slides found in archive — not a PPTX")
        }

        // 자연수 정렬 (Map 키가 Int이므로 자동으로 1, 2, ..., 10, 11 순)
        return slidesByNumber.toSortedMap()
            .map { (_, bytes) -> parseSlideXml(bytes.inputStream()) }
    }

    private fun ZipInputStream.copyAllBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var n = read(buf)
        while (n > 0) {
            out.write(buf, 0, n)
            n = read(buf)
        }
        return out.toByteArray()
    }

    private fun parseSlideXml(input: InputStream): String {
        val sb = StringBuilder()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val parser = factory.newSAXParser()

        parser.parse(input, object : DefaultHandler() {
            private var inText = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                when (pickName(localName, qName)) {
                    "t" -> inText = true
                    "br" -> sb.append('\n')
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (inText && ch != null) sb.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (pickName(localName, qName)) {
                    "t" -> inText = false
                    "p" -> sb.append('\n')      // 단락 종료
                    "tr" -> sb.append('\n')     // 표 행 종료
                    "tc" -> sb.append('\t')     // 표 셀 종료
                    "sp" -> sb.append('\n')     // 텍스트 박스(shape) 사이 간격
                }
            }

            private fun pickName(localName: String?, qName: String?): String =
                localName?.takeIf { it.isNotEmpty() }
                    ?: qName?.substringAfterLast(':')
                    ?: ""
        })
        return sb.toString().trim()
    }
}

class PptxFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
