package com.wook.viewer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderingFidelityTest {

    @Test fun `PDF는 FULL fidelity`() {
        assertEquals(RenderingFidelity.FULL, DocumentFormat.PDF.fidelity)
    }

    @Test fun `HWP는 PoC 단계에서 TEXT_ONLY`() {
        assertEquals(RenderingFidelity.TEXT_ONLY, DocumentFormat.HWP.fidelity)
    }

    @Test fun `Office 포맷도 현재 TEXT_ONLY로 보수적 표시`() {
        assertEquals(RenderingFidelity.TEXT_ONLY, DocumentFormat.DOCX.fidelity)
        assertEquals(RenderingFidelity.TEXT_ONLY, DocumentFormat.PPTX.fidelity)
    }
}
