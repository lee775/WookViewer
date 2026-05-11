package com.wook.viewer.render.xlsx

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
 * XLSX 추출 — 시트별 텍스트 + 시트별 이미지.
 *
 * 시트 ↔ 이미지 매핑 흐름:
 *   1. xl/_rels/sheet{N}.xml.rels 에서 drawing 참조 (Target="../drawings/drawingX.xml")
 *   2. xl/drawings/_rels/drawing{X}.xml.rels 에서 image 참조 (Target="../media/imageY.png")
 *   3. 모든 비트맵은 xl/media/*.png 등
 */
internal object XlsxTextExtractor {

    private val SHEET_PATH = Regex("""xl/worksheets/sheet(\d+)\.xml""")
    private val SHEET_RELS_PATH = Regex("""xl/worksheets/_rels/sheet(\d+)\.xml\.rels""")
    private val DRAWING_RELS_PATH = Regex("""xl/drawings/_rels/(drawing\d+\.xml)\.rels""")
    private const val MEDIA_PREFIX = "xl/media/"
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    data class Sheet(
        val name: String,
        val text: String,
        val images: List<Bitmap>
    )

    @Throws(Exception::class)
    fun extract(input: InputStream): List<Sheet> {
        var sharedStringsBytes: ByteArray? = null
        var workbookBytes: ByteArray? = null
        val sheetsByNumber = sortedMapOf<Int, ByteArray>()
        val sheetRelsByNumber = mutableMapOf<Int, ByteArray>()
        val drawingRelsByName = mutableMapOf<String, ByteArray>()  // drawing1.xml -> rels bytes
        val mediaByName = mutableMapOf<String, ByteArray>()        // image1.png -> bytes

        try {
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val sheetMatch = SHEET_PATH.matchEntire(name)
                    val sheetRelsMatch = SHEET_RELS_PATH.matchEntire(name)
                    val drawingRelsMatch = DRAWING_RELS_PATH.matchEntire(name)
                    when {
                        name == "xl/sharedStrings.xml" ->
                            sharedStringsBytes = zis.copyAllBytes()
                        name == "xl/workbook.xml" ->
                            workbookBytes = zis.copyAllBytes()
                        sheetMatch != null ->
                            sheetsByNumber[sheetMatch.groupValues[1].toInt()] = zis.copyAllBytes()
                        sheetRelsMatch != null ->
                            sheetRelsByNumber[sheetRelsMatch.groupValues[1].toInt()] = zis.copyAllBytes()
                        drawingRelsMatch != null ->
                            drawingRelsByName[drawingRelsMatch.groupValues[1]] = zis.copyAllBytes()
                        name.startsWith(MEDIA_PREFIX) &&
                            name.substringAfterLast('.', "").lowercase() in IMAGE_EXT ->
                            mediaByName[name.substringAfterLast('/')] = zis.copyAllBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw XlsxFormatException("Not a valid ZIP/XLSX file", e)
        }

        if (sheetsByNumber.isEmpty()) {
            throw XlsxFormatException("No sheets found")
        }

        val sharedStrings = sharedStringsBytes?.let { parseSharedStrings(it.inputStream()) } ?: emptyList()
        val sheetNames = workbookBytes?.let { parseSheetNames(it.inputStream()) } ?: emptyList()

        return sheetsByNumber.entries.mapIndexed { idx, (num, bytes) ->
            val displayName = sheetNames.getOrNull(idx) ?: "Sheet$num"
            val text = parseSheetData(bytes.inputStream(), sharedStrings)
            val images = resolveSheetImages(num, sheetRelsByNumber[num], drawingRelsByName, mediaByName)
            Sheet(displayName, text, images)
        }
    }

    private fun resolveSheetImages(
        sheetNum: Int,
        sheetRelsXml: ByteArray?,
        drawingRelsByName: Map<String, ByteArray>,
        mediaByName: Map<String, ByteArray>
    ): List<Bitmap> {
        if (sheetRelsXml == null) return emptyList()
        // 1) sheet rels → drawing 파일명 목록 (보통 1개)
        val drawingFiles = parseTargetsByType(
            sheetRelsXml.inputStream(),
            typeSuffix = "/drawing"
        ).map { it.substringAfterLast('/') }  // "../drawings/drawing1.xml" → "drawing1.xml"

        // 2) 각 drawing rels → image 파일명 목록
        val imageFileNames = drawingFiles.flatMap { drawingFile ->
            val rels = drawingRelsByName[drawingFile] ?: return@flatMap emptyList<String>()
            parseTargetsByType(rels.inputStream(), typeSuffix = "/image")
                .map { it.substringAfterLast('/') }
        }

        return imageFileNames.mapNotNull { fileName ->
            val bytes = mediaByName[fileName] ?: return@mapNotNull null
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    private fun parseTargetsByType(input: InputStream, typeSuffix: String): List<String> {
        val targets = mutableListOf<String>()
        runCatching {
            newSaxParser().parse(input, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                    if (pickName(localName, qName) == "Relationship") {
                        val type = attrs?.getValue("Type") ?: return
                        if (!type.endsWith(typeSuffix, ignoreCase = true)) return
                        val target = attrs.getValue("Target") ?: return
                        targets += target
                    }
                }
            })
        }
        return targets
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val list = mutableListOf<String>()
        val current = StringBuilder()
        newSaxParser().parse(input, object : DefaultHandler() {
            private var inSi = false
            private var inT = false
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                when (pickName(localName, qName)) {
                    "si" -> { inSi = true; current.setLength(0) }
                    "t" -> if (inSi) inT = true
                }
            }
            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (inSi && inT && ch != null) current.append(ch, start, length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (pickName(localName, qName)) {
                    "si" -> { list += current.toString(); inSi = false }
                    "t" -> inT = false
                }
            }
        })
        return list
    }

    private fun parseSheetNames(input: InputStream): List<String> {
        val names = mutableListOf<String>()
        newSaxParser().parse(input, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                if (pickName(localName, qName) == "sheet") {
                    val n = attrs?.getValue("name") ?: return
                    if (n.isNotBlank()) names += n
                }
            }
        })
        return names
    }

    private fun parseSheetData(input: InputStream, sharedStrings: List<String>): String {
        val sb = StringBuilder()
        val cellValue = StringBuilder()
        val cellInline = StringBuilder()
        newSaxParser().parse(input, object : DefaultHandler() {
            private var inRow = false
            private var inCell = false
            private var inV = false
            private var inIs = false
            private var inT = false
            private var firstCellInRow = true
            private var cellType: String? = null

            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                when (pickName(localName, qName)) {
                    "row" -> { inRow = true; firstCellInRow = true }
                    "c" -> if (inRow) {
                        inCell = true
                        cellType = attrs?.getValue("t")
                        cellValue.setLength(0); cellInline.setLength(0)
                        if (!firstCellInRow) sb.append('\t')
                        firstCellInRow = false
                    }
                    "v" -> if (inCell) inV = true
                    "is" -> if (inCell) inIs = true
                    "t" -> if (inIs || inCell) inT = true
                }
            }
            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (ch == null) return
                when {
                    inV && inCell -> cellValue.append(ch, start, length)
                    inIs && inT && inCell -> cellInline.append(ch, start, length)
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (pickName(localName, qName)) {
                    "row" -> { inRow = false; sb.append('\n') }
                    "c" -> {
                        if (inCell) {
                            sb.append(resolveCellText())
                            inCell = false
                        }
                    }
                    "v" -> inV = false
                    "is" -> inIs = false
                    "t" -> inT = false
                }
            }
            private fun resolveCellText(): String = when (cellType) {
                "s" -> {
                    val idx = cellValue.toString().trim().toIntOrNull()
                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                }
                "inlineStr" -> cellInline.toString()
                "b" -> if (cellValue.toString().trim() == "1") "TRUE" else "FALSE"
                else -> cellValue.toString().trim()
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

class XlsxFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
