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
    ),
    MARKDOWN(
        displayName = "Markdown",
        extensions = listOf("md", "markdown"),
        mimeTypes = listOf("text/markdown", "text/x-markdown"),
        fidelity = RenderingFidelity.TEXT_ONLY
    ),
    PLAIN_TEXT(
        displayName = "텍스트",
        extensions = listOf(
            "txt", "log",
            "csv", "tsv",
            "json", "xml", "yaml", "yml",
            "ini", "toml", "conf",
            "properties", "env"
        ),
        mimeTypes = listOf(
            "text/plain",
            "text/csv", "text/tab-separated-values",
            "application/json", "application/xml", "text/xml",
            "application/yaml", "text/yaml",
            "application/toml"
        ),
        fidelity = RenderingFidelity.TEXT_ONLY
    ),
    XLSX(
        displayName = "Excel",
        extensions = listOf("xls", "xlsx"),
        mimeTypes = listOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ),
        fidelity = RenderingFidelity.TEXT_ONLY
    ),
    IMAGE(
        displayName = "이미지",
        extensions = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif"),
        mimeTypes = listOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/heic",
            "image/heif"
        ),
        // FULL — 이미지는 단순화 없이 원본 그대로 표시
        fidelity = RenderingFidelity.FULL
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
