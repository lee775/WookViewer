package com.wook.viewer.render.pptx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wook.viewer.domain.model.PositionedShape
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * PPTX 추출 — 텍스트 + 임베디드 이미지 + 도형 좌표.
 *
 * 슬라이드 → 이미지 매핑:
 *   1. ppt/slides/slide{N}.xml 본문 (SAX 파싱으로 도형 좌표와 텍스트, blip rId 수집)
 *   2. ppt/slides/_rels/slide{N}.xml.rels 가 rId → 미디어 파일명 매핑
 *   3. ppt/media/ 안의 이미지 비트맵
 *   4. ppt/presentation.xml 의 p:sldSz 로 슬라이드 크기(EMU) 추출
 *
 * 결과:
 *   - 슬라이드별 [SlideContent] 리스트
 *   - SlideContent.shapes 는 절대 좌표 도형 리스트 (위치 보존)
 *   - SlideContent.text 는 검색용 평탄 텍스트
 *   - SlideContent.images 는 평탄 이미지 리스트 (폴백용)
 */
internal object PptxTextExtractor {

    private val SLIDE_PATH = Regex("""ppt/slides/slide(\d+)\.xml""")
    private val SLIDE_RELS_PATH = Regex("""ppt/slides/_rels/slide(\d+)\.xml\.rels""")
    private const val MEDIA_PREFIX = "ppt/media/"
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    /** PPTX 16:9 표준 와이드스크린. presentation.xml 에 명시 없으면 폴백. */
    private const val DEFAULT_SLIDE_W_EMU = 12_192_000L
    private const val DEFAULT_SLIDE_H_EMU = 6_858_000L

    data class SlideContent(
        val name: String,
        val text: String,
        val images: List<Bitmap>,
        val shapes: List<PositionedShape>,
        val slideWidthEmu: Long,
        val slideHeightEmu: Long
    )

    @Throws(Exception::class)
    fun extract(input: InputStream): List<SlideContent> {
        val slideXml = sortedMapOf<Int, ByteArray>()
        val slideRels = sortedMapOf<Int, ByteArray>()
        val mediaBytes = mutableMapOf<String, ByteArray>()
        var presentationBytes: ByteArray? = null

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
                            presentationBytes = zis.copyAllBytes()
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

        // 슬라이드 크기 — presentation.xml의 p:sldSz cx/cy 에서 가져옴
        val (slideW, slideH) = presentationBytes?.let { parseSlideSize(it.inputStream()) }
            ?: (DEFAULT_SLIDE_W_EMU to DEFAULT_SLIDE_H_EMU)

        return slideXml.entries.mapIndexed { idx, (num, bytes) ->
            val rIdToBitmap = resolveSlideImageMap(slideRels[num], mediaBytes)
            val parsed = parseSlide(bytes.inputStream(), rIdToBitmap)
            SlideContent(
                name = "Slide $num",
                text = parsed.flatText,
                images = parsed.shapes.mapNotNull { it.bitmap },
                shapes = parsed.shapes,
                slideWidthEmu = slideW,
                slideHeightEmu = slideH
            )
        }
    }

    private fun isImageEntry(name: String): Boolean {
        if (!name.startsWith(MEDIA_PREFIX)) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXT
    }

    private fun parseSlideSize(input: InputStream): Pair<Long, Long> {
        var w = DEFAULT_SLIDE_W_EMU
        var h = DEFAULT_SLIDE_H_EMU
        runCatching {
            newSaxParser().parse(input, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                    if (pickName(localName, qName) == "sldSz") {
                        attrs?.getValue("cx")?.toLongOrNull()?.let { w = it }
                        attrs?.getValue("cy")?.toLongOrNull()?.let { h = it }
                    }
                }
            })
        }
        return w to h
    }

    private fun resolveSlideImageMap(
        relsXml: ByteArray?,
        mediaByName: Map<String, ByteArray>
    ): Map<String, Bitmap> {
        if (relsXml == null) return emptyMap()
        val rIdToFile = mutableMapOf<String, String>()
        runCatching {
            newSaxParser().parse(relsXml.inputStream(), object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                    if (pickName(localName, qName) == "Relationship") {
                        val type = attrs?.getValue("Type") ?: return
                        if (!type.endsWith("/image", ignoreCase = true)) return
                        val id = attrs.getValue("Id") ?: return
                        val target = attrs.getValue("Target") ?: return
                        rIdToFile[id] = target
                    }
                }
            })
        }
        return rIdToFile.mapNotNull { (id, target) ->
            val fileName = target.substringAfterLast('/')
            val bytes = mediaByName[fileName] ?: return@mapNotNull null
            val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            bmp?.let { id to it }
        }.toMap()
    }

    private data class ParsedSlide(
        val flatText: String,
        val shapes: List<PositionedShape>
    )

    /**
     * 슬라이드 XML을 SAX로 순회하며 도형(p:sp, p:pic)을 모음.
     * 각 도형의 a:xfrm/a:off + a:ext 로 좌표/크기 추출.
     * 텍스트는 도형별 t 누적 + 평탄 흐름용 fallback string도 생성.
     */
    private fun parseSlide(input: InputStream, rIdToBitmap: Map<String, Bitmap>): ParsedSlide {
        val shapes = mutableListOf<PositionedShape>()
        val flatText = StringBuilder()

        newSaxParser().parse(input, object : DefaultHandler() {
            // 도형(sp/pic) 깊이 추적 — 중첩된 a:xfrm 이 도형 밖에 나오는 걸 막음
            private var inShape = false
            private var isPic = false
            private var inSpPr = false  // a:xfrm 은 spPr 안에서만 유효
            private var inXfrm = false
            private var inTxBody = false
            private var inRun = false
            private var inT = false
            private var inBlip = false  // pic 안 a:blipFill/a:blip

            private var shapeX: Long = 0
            private var shapeY: Long = 0
            private var shapeW: Long = 0
            private var shapeH: Long = 0
            private var shapeText = StringBuilder()
            private var shapeRId: String? = null

            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                val name = pickName(localName, qName)
                when (name) {
                    "sp", "pic" -> {
                        inShape = true
                        isPic = (name == "pic")
                        shapeX = 0; shapeY = 0; shapeW = 0; shapeH = 0
                        shapeText.setLength(0)
                        shapeRId = null
                    }
                    "spPr" -> if (inShape) inSpPr = true
                    "xfrm" -> if (inShape && inSpPr) inXfrm = true
                    "off" -> if (inShape && inXfrm) {
                        attrs?.getValue("x")?.toLongOrNull()?.let { shapeX = it }
                        attrs?.getValue("y")?.toLongOrNull()?.let { shapeY = it }
                    }
                    "ext" -> if (inShape && inXfrm) {
                        attrs?.getValue("cx")?.toLongOrNull()?.let { shapeW = it }
                        attrs?.getValue("cy")?.toLongOrNull()?.let { shapeH = it }
                    }
                    "txBody" -> if (inShape) inTxBody = true
                    "r" -> if (inTxBody) inRun = true
                    "t" -> if (inTxBody) inT = true
                    "blip" -> if (inShape && isPic) {
                        val embed = attrs?.getValue(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                            "embed"
                        ) ?: attrs?.getValue("r:embed") ?: attrs?.getValue("embed")
                        if (!embed.isNullOrBlank()) shapeRId = embed
                    }
                    "br" -> if (inTxBody) shapeText.append('\n')
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (inT && inTxBody && ch != null) {
                    shapeText.append(ch, start, length)
                    flatText.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (pickName(localName, qName)) {
                    "sp", "pic" -> {
                        if (inShape) {
                            val text = shapeText.toString().trim().ifBlank { null }
                            val bmp = shapeRId?.let { rIdToBitmap[it] }
                            // 좌표/크기 0이면 placeholder가 layout 상속한 경우 — 슬라이드 전체로 폴백
                            if (text != null || bmp != null) {
                                shapes += PositionedShape(
                                    xEmu = shapeX,
                                    yEmu = shapeY,
                                    widthEmu = if (shapeW > 0) shapeW else 0,
                                    heightEmu = if (shapeH > 0) shapeH else 0,
                                    text = text,
                                    bitmap = bmp
                                )
                            }
                        }
                        inShape = false
                        isPic = false
                    }
                    "spPr" -> inSpPr = false
                    "xfrm" -> inXfrm = false
                    "txBody" -> inTxBody = false
                    "r" -> inRun = false
                    "t" -> inT = false
                    "p" -> if (inTxBody) {
                        shapeText.append('\n')
                        flatText.append('\n')
                    }
                }
            }
        })

        return ParsedSlide(
            flatText = flatText.toString().trim(),
            shapes = shapes
        )
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
        while (n > 0) { out.write(buf, 0, n); n = read(buf) }
        return out.toByteArray()
    }
}

class PptxFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
