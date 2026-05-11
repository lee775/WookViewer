package com.wook.viewer.render.docx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * DOCX 본문 텍스트 + 임베디드 이미지 추출.
 *
 * 이미지는 `word/media/` 아래 png/jpg/jpeg/gif/bmp/webp 파일. 위치 정보는 어렵게
 * 매핑해야 하므로 일단 단순히 "이 문서에 들어있는 이미지 전부"를 반환한다.
 */
internal object DocxTextExtractor {

    private const val DOCUMENT_XML_PATH = "word/document.xml"
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    data class DocxContent(
        val text: String,
        val images: List<Bitmap>
    )

    @Throws(Exception::class)
    fun extract(input: InputStream): DocxContent {
        var xmlBytes: ByteArray? = null
        val imageBytesList = mutableListOf<ByteArray>()

        try {
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == DOCUMENT_XML_PATH -> xmlBytes = zis.copyAllBytes()
                        isImageEntry(name, "word/media/") -> imageBytesList.add(zis.copyAllBytes())
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw DocxFormatException("Not a valid ZIP/DOCX file", e)
        }

        val xml = xmlBytes ?: throw DocxFormatException("word/document.xml not found — not a DOCX")
        val text = parseDocumentXml(xml.inputStream())
        val images = imageBytesList.mapNotNull { decodeBitmap(it) }
        return DocxContent(text, images)
    }

    private fun isImageEntry(name: String, prefix: String): Boolean {
        if (!name.startsWith(prefix)) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXT
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? = runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

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
                    "p" -> sb.append('\n')
                    "tr" -> sb.append('\n')
                    "tc" -> sb.append('\t')
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

class DocxFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
