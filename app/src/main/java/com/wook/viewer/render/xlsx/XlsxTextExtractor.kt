package com.wook.viewer.render.xlsx

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * XLSX (.xlsx) 시트별 텍스트 추출기.
 *
 * XLSX 구조:
 *   - .xlsx = ZIP
 *   - xl/sharedStrings.xml — 셀 문자열 풀 (Excel은 문자열 중복 제거 위해 풀에 저장 후 인덱스 참조)
 *   - xl/worksheets/sheet1.xml, sheet2.xml, ... — 각 시트 데이터
 *   - xl/workbook.xml — 시트 이름/순서
 *
 * 셀 타입:
 *   - t="s"        — sharedStrings 인덱스
 *   - t="inlineStr"— <is><t>...</t></is>
 *   - t="str"      — 수식 결과 문자열 (<v> 안)
 *   - t="b"        — 불린 (1=TRUE, 0=FALSE)
 *   - (없음)        — 숫자/일반 (<v> 안 raw)
 *
 * 출력: 시트별 표 텍스트 — 셀은 탭, 행은 newline.
 */
internal object XlsxTextExtractor {

    private val SHEET_PATH = Regex("""xl/worksheets/sheet(\d+)\.xml""")

    data class SheetText(val name: String, val text: String)

    @Throws(Exception::class)
    fun extract(input: InputStream): List<SheetText> {
        var sharedStringsBytes: ByteArray? = null
        var workbookBytes: ByteArray? = null
        val sheetsByNumber = sortedMapOf<Int, ByteArray>()

        try {
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "xl/sharedStrings.xml" ->
                            sharedStringsBytes = zis.readAllBytesPolyfill()
                        name == "xl/workbook.xml" ->
                            workbookBytes = zis.readAllBytesPolyfill()
                        else -> {
                            val match = SHEET_PATH.matchEntire(name)
                            if (match != null) {
                                val num = match.groupValues[1].toInt()
                                sheetsByNumber[num] = zis.readAllBytesPolyfill()
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw XlsxFormatException("Not a valid ZIP/XLSX file", e)
        }

        if (sheetsByNumber.isEmpty()) {
            throw XlsxFormatException("No sheets found — not an XLSX")
        }

        val sharedStrings = sharedStringsBytes?.let { parseSharedStrings(it.inputStream()) } ?: emptyList()
        val sheetNames = workbookBytes?.let { parseSheetNames(it.inputStream()) } ?: emptyList()

        return sheetsByNumber.entries.mapIndexed { idx, (num, bytes) ->
            val displayName = sheetNames.getOrNull(idx) ?: "Sheet$num"
            val text = parseSheetData(bytes.inputStream(), sharedStrings)
            SheetText(name = displayName, text = text)
        }
    }

    private fun ZipInputStream.readAllBytesPolyfill(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var n = read(buf)
        while (n > 0) {
            out.write(buf, 0, n)
            n = read(buf)
        }
        return out.toByteArray()
    }

    // ---- sharedStrings.xml 파싱 ----

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

    // ---- workbook.xml 시트 이름 파싱 ----

    private fun parseSheetNames(input: InputStream): List<String> {
        val names = mutableListOf<String>()
        newSaxParser().parse(input, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes?) {
                if (pickName(localName, qName) == "sheet") {
                    val name = attrs?.getValue("name") ?: return
                    if (name.isNotBlank()) names += name
                }
            }
        })
        return names
    }

    // ---- sheet*.xml 데이터 파싱 ----

    private fun parseSheetData(input: InputStream, sharedStrings: List<String>): String {
        val sb = StringBuilder()
        val cellValueBuf = StringBuilder()
        val cellInlineBuf = StringBuilder()

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
                        cellValueBuf.setLength(0)
                        cellInlineBuf.setLength(0)
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
                    inV && inCell -> cellValueBuf.append(ch, start, length)
                    inIs && inT && inCell -> cellInlineBuf.append(ch, start, length)
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
                    val idx = cellValueBuf.toString().trim().toIntOrNull()
                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                }
                "inlineStr" -> cellInlineBuf.toString()
                "b" -> if (cellValueBuf.toString().trim() == "1") "TRUE" else "FALSE"
                else -> cellValueBuf.toString().trim()
            }
        })

        return sb.toString().trim()
    }

    // ---- 공통 헬퍼 ----

    private fun newSaxParser(): javax.xml.parsers.SAXParser =
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }.newSAXParser()

    private fun pickName(localName: String?, qName: String?): String =
        localName?.takeIf { it.isNotEmpty() } ?: qName?.substringAfterLast(':') ?: ""
}

class XlsxFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
