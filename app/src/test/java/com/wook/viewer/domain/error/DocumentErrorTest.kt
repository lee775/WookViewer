package com.wook.viewer.domain.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DocumentErrorTest {

    @Test fun `IoError는 cause 보존`() {
        val cause = RuntimeException("disk full")
        val e = DocumentError.IoError(cause)
        assertEquals(cause, e.cause)
    }

    @Test fun `PasswordProtected는 별도 타입`() {
        val e = DocumentError.PasswordProtected()
        assertNotNull(e)
    }

    @Test fun `UnsupportedVariant는 variant 정보 보존`() {
        val e = DocumentError.UnsupportedVariant("hwp 3.0")
        assertEquals("hwp 3.0", e.variant)
    }

    @Test fun `각 에러는 sealed 타입 - when 분기 가능`() {
        val errors: List<DocumentError> = listOf(
            DocumentError.IoError(),
            DocumentError.PasswordProtected(),
            DocumentError.Corrupted(),
            DocumentError.UnsupportedVariant("x"),
            DocumentError.Unknown()
        )
        // 컴파일 시점에 모든 분기 강제
        errors.forEach { e ->
            val name = when (e) {
                is DocumentError.IoError -> "io"
                is DocumentError.PasswordProtected -> "password"
                is DocumentError.Corrupted -> "corrupted"
                is DocumentError.UnsupportedVariant -> "unsupported"
                is DocumentError.Unknown -> "unknown"
            }
            assertNotNull(name)
        }
    }
}
