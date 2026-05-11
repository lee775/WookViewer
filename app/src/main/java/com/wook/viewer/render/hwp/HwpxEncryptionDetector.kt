package com.wook.viewer.render.hwp

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/**
 * HWPX(ZIP 컨테이너) 파일이 암호화되었는지 META-INF/manifest.xml 을 검사해 판정.
 *
 * OpenDocument/HWPX 매니페스트에서 각 file-entry 의 자식으로 `<encryption-data>`
 * 가 있으면 그 파일이 암호화된 상태. 패키지 전체가 암호화되면 보통 모든 항목에 붙는다.
 */
internal object HwpxEncryptionDetector {

    private const val MANIFEST_ENTRY = "META-INF/manifest.xml"

    /** 어떤 파일 항목이라도 encryption-data를 갖고 있으면 true. */
    fun isEncrypted(file: File): Boolean = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(MANIFEST_ENTRY) ?: return@use false
            zip.getInputStream(entry).use { input ->
                var hit = false
                val parser = SAXParserFactory.newInstance().apply {
                    isNamespaceAware = true
                    runCatching {
                        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    }
                }.newSAXParser()
                parser.parse(input, object : DefaultHandler() {
                    override fun startElement(
                        uri: String?, localName: String?, qName: String?, attrs: Attributes?
                    ) {
                        val name = localName?.takeIf { it.isNotEmpty() }
                            ?: qName?.substringAfterLast(':')
                            ?: ""
                        if (name == "encryption-data") hit = true
                    }
                })
                hit
            }
        }
    }.onFailure { Timber.w(it, "HWPX manifest 검사 실패 — 평문으로 가정") }
      .getOrDefault(false)
}
