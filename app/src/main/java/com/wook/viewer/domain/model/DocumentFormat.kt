package com.wook.viewer.domain.model

enum class DocumentFormat(
    val displayName: String,
    val extensions: List<String>,
    val mimeTypes: List<String>,
    val fidelity: RenderingFidelity
) {
    PDF(
        displayName = "PDF",
        extensions = listOf("pdf"),
        mimeTypes = listOf("application/pdf"),
        fidelity = RenderingFidelity.FULL
    ),
    DOCX(
        displayName = "Word",
        extensions = listOf("doc", "docx"),
        mimeTypes = listOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ),
        // 향후 구현 시 정확한 충실도로 변경
        fidelity = RenderingFidelity.TEXT_ONLY
    ),
    PPTX(
        displayName = "PowerPoint",
        extensions = listOf("ppt", "pptx"),
        mimeTypes = listOf(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        ),
        fidelity = RenderingFidelity.TEXT_ONLY
    ),
    HWP(
        displayName = "한글",
        extensions = listOf("hwp", "hwpx"),
        mimeTypes = listOf(
            "application/x-hwp",
            "application/haansofthwp",
            "application/vnd.hancom.hwpx"
        ),
        fidelity = RenderingFidelity.TEXT_ONLY
    );

    companion object {
        fun fromExtension(name: String): DocumentFormat? {
            val ext = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }

        fun fromMimeType(mime: String?): DocumentFormat? {
            if (mime == null) return null
            return entries.firstOrNull { mime in it.mimeTypes }
        }
    }
}
