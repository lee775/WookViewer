package com.wook.viewer.render.docx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wook.viewer.domain.model.PageElement
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * DOCX 본문 추출 — 텍스트 + 인라인 이미지(원본 순서 유지).
 *
 * 파싱 절차:
 *   1. word/_rels/document.xml.rels 에서 rId → media 파일명 매핑 작성
 *   2. word/media/ 안의 png/jpg/... 비트맵으로 디코드
 *   3. word/document.xml 을 SAX로 순회하며 텍스트와 a:blip 의 r:embed 를 만나는 순간
 *      현재까지의 텍스트를 끊고 ImageElement 삽입, 다시 다음 TextElement 시작
 *
 * 결과: 한 문서를 PageElement 의 평탄한 리스트로 반환. UI는 이걸 그대로
 * (텍스트 → 이미지 → 텍스트) 순서대로 그린다.
 */
internal object DocxTextExtractor {

    private const val DOCUMENT_XML_PATH = "word/document.xml"
    private const val DOCUMENT_RELS_PATH = "word/_rels/document.xml.rels"
    private const val MEDIA_PREFIX = "word/media/"
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
    private const val OOXML_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    data class DocxContent(
        val elements: List<PageElement>,
        val text: String,
        val images: List<Bitmap>
    )

    @Throws(Exception::class)
    fun extract(input: InputStream): DocxContent {
        var xmlBytes: ByteArray? = null
        var relsBytes: ByteArray? = null
        val mediaByName = mutableMapOf<String, ByteArray>()

        try {
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == DOCUMENT_XML_PATH -> xmlBytes = zis.copyAllBytes()
                        name == DOCUMENT_RELS_PATH -> relsBytes = zis.copyAllBytes()
                        isImageEntry(name) ->
                            mediaByName[name.substringAfterLast('/')] = zis.copyAllBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw DocxFormatException("Not a valid ZIP/DOCX file", e)
        }

        val xml = xmlBytes ?: throw DocxFormatException("word/document.xml not found — not a DOCX")

        // rId → 파일명 매핑 + 비트맵 캐시
        val rIdToBitmap: Map<String, Bitmap> = relsBytes?.let { rb ->
            val rIdToFile = parseRelationships(rb.inputStream())
            rIdToFile.entries.mapNotNull { (rId, target) ->
                val fileName = target.substringAfterLast('/')
                val bytes = mediaByName[fileName] ?: return@mapNotNull null
                val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                bmp?.let { rId to it }
            }.toMap()
        } ?: emptyMap()

        val elements = parseDocumentXml(xml.inputStream(), rIdToBitmap)
        val plainText = elements.filterIsInstance<PageElement.TextElement>()
            .joinToString("") { it.text }
            .trim()
        val allImages = elements.filterIsInstance<PageElement.ImageElement>()
            .map { it.bitmap }
        return DocxContent(elements, plainText, allImages)
    }

    private fun isImageEntry(name: String): Boolean {
        if (!name.startsWith(MEDIA_PREFIX)) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXT
    }

    private fun parseRelationships(input: InputStream): Map<String, String> {
        val map = mutableMapOf<String, String>()
        runCatching {
            newSaxParser().parse(input, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                    if (pickName(localName, qName) == "Relationship") {
                        val type = attrs?.getValue("Type") ?: return
                        if (!type.endsWith("/image", ignoreCase = true)) return
                        val id = attrs.getValue("Id") ?: return
                        val target = attrs.getValue("Target") ?: return
                        map[id] = target
                    }
                }
            })
        }
        return map
    }

    private fun parseDocumentXml(
        input: InputStream,
        rIdToBitmap: Map<String, Bitmap>
    ): List<PageElement> {
        val elements = mutableListOf<PageElement>()
        val currentText = StringBuilder()

        fun flushText() {
            if (currentText.isNotEmpty()) {
                elements += PageElement.TextElement(currentText.toString())
                currentText.clear()
            }
        }

        newSaxParser().parse(input, object : DefaultHandler() {
            private var inText = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                val name = pickName(localName, qName)
                when (name) {
                    "t" -> inText = true
                    "tab" -> currentText.append('\t')
                    "br", "cr" -> currentText.append('\n')
                    "blip" -> {
                        // r:embed 속성 — namespace 인지하든 안 하든 모두 시도
                        val rId = attrs?.getValue(OOXML_REL_NS, "embed")
                            ?: attrs?.getValue("r:embed")
                            ?: attrs?.getValue("embed")
                        if (!rId.isNullOrBlank()) {
                            val bmp = rIdToBitmap[rId]
                            if (bmp != null) {
                                flushText()
                                elements += PageElement.ImageElement(bmp)
                            }
                        }
                    }
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (inText && ch != null) currentText.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (pickName(localName, qName)) {
                    "t" -> inText = false
                    "p" -> currentText.append('\n')
                    "tr" -> currentText.append('\n')
                    "tc" -> currentText.append('\t')
                }
            }
        })
        flushText()
        return elements
    }

    private fun newSaxParser(): javax.xml.parsers.SAXParser =
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }.newSAXParser()

    private fun pickName(localName: String?, qName: String?): String =
        localName?.takeIf { it.isNotEmpty() }
            ?: qName?.substringAfterLast(':')
            ?: ""

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
}

class DocxFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
