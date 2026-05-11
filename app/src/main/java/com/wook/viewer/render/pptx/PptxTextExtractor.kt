package com.wook.viewer.render.pptx

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
 * PPTX 슬라이드별 텍스트 + 슬라이드에 참조된 임베디드 이미지 추출.
 *
 * 슬라이드 → 이미지 매핑 흐름:
 *   1. ppt/slides/slide{N}.xml 본문
 *   2. ppt/slides/_rels/slide{N}.xml.rels 가 r:id → 미디어 경로 ("../media/image1.png") 매핑
 *   3. ppt/media/*.png|jpg|... 실제 바이트
 *
 * 따라서 각 슬라이드는 자기 rels에 명시된 이미지만 보유. 모든 이미지가 모든
 * 슬라이드에 중복 표시되는 일은 없음.
 */
internal object PptxTextExtractor {

    private val SLIDE_PATH = Regex("""ppt/slides/slide(\d+)\.xml""")
    private val SLIDE_RELS_PATH = Regex("""ppt/slides/_rels/slide(\d+)\.xml\.rels""")
    private val MEDIA_PREFIX = "ppt/media/"
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    data class SlideContent(
        val name: String,
        val text: String,
        val images: List<Bitmap>
    )

    @Throws(Exception::class)
    fun extract(input: InputStream): List<SlideContent> {
        val slideXml = sortedMapOf<Int, ByteArray>()
        val slideRels = sortedMapOf<Int, ByteArray>()
        val mediaBytes = mutableMapOf<String, ByteArray>()  // 파일명만 키로
        var workbookBytes: ByteArray? = null

        try {
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val slideMatch = SLIDE_PATH.matchEntire(name)
                    val relsMatch = SLIDE_RELS_PATH.matchEntire(name)
                    when {
                        slideMatch != null ->
                            slideXml[slideMatch.groupValues[1].toInt()] = zis.copyAllBytes()
                        relsMatch != null ->
                            slideRels[relsMatch.groupValues[1].toInt()] = zis.copyAllBytes()
                        name == "ppt/presentation.xml" ->
                            workbookBytes = zis.copyAllBytes()
                        isImageEntry(name) ->
                            mediaBytes[name.substringAfterLast('/')] = zis.copyAllBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw PptxFormatException("Not a valid ZIP/PPTX file", e)
        }

        if (slideXml.isEmpty()) {
            throw PptxFormatException("No slides found in archive — not a PPTX")
        }

        val sheetNames = workbookBytes?.let { parseSlideTitles(it.inputStream()) } ?: emptyList()

        return slideXml.entries.mapIndexed { idx, (num, bytes) ->
            val displayName = sheetNames.getOrNull(idx) ?: "Slide $num"
            val text = parseSlideXml(bytes.inputStream())
            val images = collectSlideImages(slideRels[num], mediaBytes)
            SlideContent(displayName, text, images)
        }
    }

    private fun isImageEntry(name: String): Boolean {
        if (!name.startsWith(MEDIA_PREFIX)) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXT
    }

    /** slideN.xml.rels 안에서 image 타입 Relationship의 Target만 추출 → 미디어 매핑. */
    private fun collectSlideImages(
        relsXml: ByteArray?,
        mediaByName: Map<String, ByteArray>
    ): List<Bitmap> {
        if (relsXml == null) return emptyList()
        val targets = mutableListOf<String>()

        runCatching {
            newSaxParser().parse(relsXml.inputStream(), object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                    if (pickName(localName, qName) == "Relationship") {
                        val type = attrs?.getValue("Type") ?: return
                        val target = attrs.getValue("Target") ?: return
                        if (type.endsWith("/image", ignoreCase = true) && target.isNotBlank()) {
                            targets += target
                        }
                    }
                }
            })
        }

        return targets.mapNotNull { target ->
            val fileName = target.substringAfterLast('/')
            val bytes = mediaByName[fileName] ?: return@mapNotNull null
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    private fun parseSlideTitles(input: InputStream): List<String> {
        // presentation.xml 자체에서 슬라이드 이름이 직접 나오진 않으므로 placeholder.
        // 향후 _rels/presentation.xml.rels + sldId 매핑으로 정확한 순서를 잡을 수도 있음.
        return emptyList()
    }

    private fun parseSlideXml(input: InputStream): String {
        val sb = StringBuilder()
        newSaxParser().parse(input, object : DefaultHandler() {
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
                    "p" -> sb.append('\n')
                    "tr" -> sb.append('\n')
                    "tc" -> sb.append('\t')
                    "sp" -> sb.append('\n')
                }
            }
        })
        return sb.toString().trim()
    }

    private fun newSaxParser(): javax.xml.parsers.SAXParser =
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }.newSAXParser()

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

    private fun pickName(localName: String?, qName: String?): String =
        localName?.takeIf { it.isNotEmpty() }
            ?: qName?.substringAfterLast(':')
            ?: ""
}

class PptxFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
