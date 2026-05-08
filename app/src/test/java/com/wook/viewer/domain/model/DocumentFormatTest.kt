package com.wook.viewer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentFormatTest {

    @Test fun `pdf extension`() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromExtension("report.PDF"))
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromExtension("a.b.pdf"))
    }

    @Test fun `docx and doc`() {
        assertEquals(DocumentFormat.DOCX, DocumentFormat.fromExtension("a.docx"))
        assertEquals(DocumentFormat.DOCX, DocumentFormat.fromExtension("a.doc"))
    }

    @Test fun `pptx and ppt`() {
        assertEquals(DocumentFormat.PPTX, DocumentFormat.fromExtension("a.pptx"))
        assertEquals(DocumentFormat.PPTX, DocumentFormat.fromExtension("a.PPT"))
    }

    @Test fun `hwp and hwpx`() {
        assertEquals(DocumentFormat.HWP, DocumentFormat.fromExtension("a.hwp"))
        assertEquals(DocumentFormat.HWP, DocumentFormat.fromExtension("a.hwpx"))
    }

    @Test fun `markdown extensions`() {
        assertEquals(DocumentFormat.MARKDOWN, DocumentFormat.fromExtension("README.md"))
        assertEquals(DocumentFormat.MARKDOWN, DocumentFormat.fromExtension("doc.markdown"))
    }

    @Test fun `plain text extensions`() {
        assertEquals(DocumentFormat.PLAIN_TEXT, DocumentFormat.fromExtension("notes.txt"))
        assertEquals(DocumentFormat.PLAIN_TEXT, DocumentFormat.fromExtension("data.csv"))
        assertEquals(DocumentFormat.PLAIN_TEXT, DocumentFormat.fromExtension("config.json"))
        assertEquals(DocumentFormat.PLAIN_TEXT, DocumentFormat.fromExtension("settings.yaml"))
        assertEquals(DocumentFormat.PLAIN_TEXT, DocumentFormat.fromExtension("server.log"))
    }

    @Test fun `xlsx and xls`() {
        assertEquals(DocumentFormat.XLSX, DocumentFormat.fromExtension("budget.xlsx"))
        assertEquals(DocumentFormat.XLSX, DocumentFormat.fromExtension("data.xls"))
    }

    @Test fun `image extensions`() {
        assertEquals(DocumentFormat.IMAGE, DocumentFormat.fromExtension("photo.jpg"))
        assertEquals(DocumentFormat.IMAGE, DocumentFormat.fromExtension("photo.JPEG"))
        assertEquals(DocumentFormat.IMAGE, DocumentFormat.fromExtension("logo.png"))
        assertEquals(DocumentFormat.IMAGE, DocumentFormat.fromExtension("anim.gif"))
        assertEquals(DocumentFormat.IMAGE, DocumentFormat.fromExtension("photo.heic"))
        assertEquals(DocumentFormat.IMAGE, DocumentFormat.fromExtension("img.webp"))
    }

    @Test fun `unknown returns null`() {
        assertNull(DocumentFormat.fromExtension("noext"))
        assertNull(DocumentFormat.fromExtension("a.unknownext"))
    }

    @Test fun `mime type lookup`() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromMimeType("application/pdf"))
        assertEquals(
            DocumentFormat.DOCX,
            DocumentFormat.fromMimeType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
        assertEquals(DocumentFormat.PLAIN_TEXT, DocumentFormat.fromMimeType("text/plain"))
        assertEquals(DocumentFormat.MARKDOWN, DocumentFormat.fromMimeType("text/markdown"))
        assertNull(DocumentFormat.fromMimeType("application/x-unknown"))
        assertNull(DocumentFormat.fromMimeType(null))
    }
}
