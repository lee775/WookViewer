package com.wook.viewer.presentation.viewer.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wook.viewer.R

/**
 * 공유 옵션 시트.
 *
 * @param canShareText 현재 페이지 텍스트 추출이 가능한 포맷이면 true
 *                     (TEXT_ONLY 포맷 + PDF). 이미지는 false.
 */
@Composable
fun ShareSheet(
    canShareText: Boolean,
    onShareOriginal: () -> Unit,
    onSharePageText: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.share_sheet_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            ShareRow(
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                label = stringResource(R.string.share_original),
                onClick = {
                    onShareOriginal()
                    onDismiss()
                }
            )
            if (canShareText) {
                ShareRow(
                    icon = Icons.Filled.TextFields,
                    label = stringResource(R.string.share_page_text),
                    onClick = {
                        onSharePageText()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ShareRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp
        )
    }
}

/**
 * 원본 파일을 다른 앱으로 전달.
 *
 * SAF URI(content://)는 [Intent.FLAG_GRANT_READ_URI_PERMISSION] 와 함께 보내면
 * 수신 앱이 원본 제공자의 권한을 일시적으로 위임받아 읽을 수 있다.
 */
fun shareOriginalDocument(
    context: Context,
    uri: Uri,
    mimeType: String,
    subject: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.share_chooser_title))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** 현재 페이지의 평문 텍스트를 ACTION_SEND 로 공유. */
fun sharePlainText(
    context: Context,
    text: String,
    subject: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.share_chooser_title))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
