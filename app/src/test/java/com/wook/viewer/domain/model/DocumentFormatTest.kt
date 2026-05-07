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

    @Test fun `unknown returns null`() {
        assertNull(DocumentFormat.fromExtension("a.txt"))
        assertNull(DocumentFormat.fromExtension("noext"))
    }

    @Test fun `mime type lookup`() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromMimeType("application/pdf"))
        assertEquals(
            DocumentFormat.DOCX,
            DocumentFormat.fromMimeType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
        assertNull(DocumentFormat.fromMimeType("text/plain"))
        assertNull(DocumentFormat.fromMimeType(null))
    }
}
