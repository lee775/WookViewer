package com.wook.viewer.render.docx

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * DOCX (.docx) 본문 텍스트 추출기.
 *
 * DOCX 구조:
 *   - .docx = ZIP
 *   - 본문은 `word/document.xml` (WordprocessingML)
 *   - `<w:p>` 단락, `<w:r>` 런(run), `<w:t>` 텍스트
 *   - `<w:tbl>/<w:tr>/<w:tc>` 표
 *
 * 처리 대상:
 *   - 본문 단락/표 텍스트
 *   - tab, line break
 *
 * 무시:
 *   - 머리말/꼬리말 (header.xml/footer.xml)
 *   - 댓글, 트랙체인지
 *   - 이미지, 도형, SmartArt
 *   - 서식 (글꼴, 색상, 정렬)
 *
 * 의존성: java.util.zip + javax.xml SAX (JVM 표준) — 외부 라이브러리 0개.
 */
internal object DocxTextExtractor {

    private const val DOCUMENT_XML_PATH = "word/document.xml"

    /**
     * @throws java.io.IOException ZIP/I-O 오류
     * @throws DocxFormatException ZIP이 아니거나 word/document.xml이 없음 (예: 구형 .doc 바이너리)
     * @throws org.xml.sax.SAXException XML 파싱 오류
     */
    @Throws(Exception::class)
    fun extract(input: InputStream): String {
        val xmlBytes = readDocumentXmlFromZip(input)
            ?: throw DocxFormatException("word/document.xml not found — not a DOCX")
        return parseDocumentXml(xmlBytes.inputStream())
    }

    private fun readDocumentXmlFromZip(input: InputStream): ByteArray? {
        try {
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == DOCUMENT_XML_PATH) {
                        return zis.copyAllBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: ZipException) {
            // 바이너리 .doc, 손상된 zip 등
            throw DocxFormatException("Not a valid ZIP/DOCX file", e)
        }
        return null
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

    private fun parseDocumentXml(input: InputStream): String {
        val sb = StringBuilder()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // XXE 방어 — 외부 entity 참조 비활성
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val parser = factory.newSAXParser()

        parser.parse(input, object : DefaultHandler() {
            private var inText = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                val name = pickName(localName, qName)
                when (name) {
                    "t" -> inText = true
                    "tab" -> sb.append('\t')
                    "br", "cr" -> sb.append('\n')
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (inText && ch != null) sb.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val name = pickName(localName, qName)
                when (name) {
                    "t" -> inText = false
                    "p" -> sb.append('\n')      // 단락 종료
                    "tr" -> sb.append('\n')     // 표 행 종료
                    "tc" -> sb.append('\t')     // 표 셀 종료
                }
            }

            /** namespace-aware일 때 localName 사용, 아니면 qName에서 prefix 제거. */
            private fun pickName(localName: String?, qName: String?): String =
                localName?.takeIf { it.isNotEmpty() }
                    ?: qName?.substringAfterLast(':')
                    ?: ""
        })
        return sb.toString().trim()
    }
}

class DocxFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
