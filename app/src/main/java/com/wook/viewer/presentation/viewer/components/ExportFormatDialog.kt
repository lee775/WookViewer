package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wook.viewer.domain.model.DocumentFormat

/**
 * Office 파일 → 다른 포맷 변환 저장 옵션 1개.
 * @property lokFormat LOK saveAs 가 받는 포맷 단축명 ("pdf", "odt", "docx" 등)
 * @property label UI 표시 텍스트
 * @property defaultFileName SAF CreateDocument 에 기본값으로 전달
 */
data class ExportOption(
    val lokFormat: String,
    val label: String,
    val defaultFileName: String,
)

/**
 * Office 파일에서 "다른 포맷으로 저장" 진입 시 표시되는 다이얼로그.
 * 원본 포맷별로 변환 가능 옵션이 다름:
 *  - DOCX: PDF / ODT / DOCX
 *  - PPTX: PDF / ODP / PPTX
 *  - XLSX: PDF / ODS / XLSX
 *
 * PDF 는 모든 Office 포맷에서 공통.
 */
@Composable
fun ExportFormatDialog(
    sourceFormat: DocumentFormat,
    sourceDisplayName: String,
    onSelect: (ExportOption) -> Unit,
    onDismiss: () -> Unit
) {
    val baseName = sourceDisplayName.substringBeforeLast('.', sourceDisplayName)
        .ifBlank { "document" }

    val options = buildList {
        add(ExportOption("pdf", "PDF", "$baseName.pdf"))
        when (sourceFormat) {
            DocumentFormat.DOCX -> {
                add(ExportOption("odt", "ODT (오픈 문서)", "$baseName.odt"))
                add(ExportOption("docx", "DOCX (다른 이름으로)", "${baseName}_copy.docx"))
            }
            DocumentFormat.PPTX -> {
                add(ExportOption("odp", "ODP (오픈 프레젠테이션)", "$baseName.odp"))
                add(ExportOption("pptx", "PPTX (다른 이름으로)", "${baseName}_copy.pptx"))
            }
            DocumentFormat.XLSX -> {
                add(ExportOption("ods", "ODS (오픈 스프레드시트)", "$baseName.ods"))
                add(ExportOption("xlsx", "XLSX (다른 이름으로)", "${baseName}_copy.xlsx"))
            }
            else -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("다른 포맷으로 저장") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                options.forEach { opt ->
                    TextButton(
                        onClick = { onSelect(opt) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = opt.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
