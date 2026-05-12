package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 편집 모드 상단바 — 취소 / 저장 / 다른 이름으로 저장.
 * SearchBar 와 같은 패턴 — 편집 활성 시 일반 ViewerTopBar 를 대체.
 */
@Composable
fun EditTopBar(
    documentName: String,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel, enabled = !saving) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "편집 취소",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = documentName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp),
                strokeWidth = 2.dp
            )
        }

        IconButton(onClick = onSaveAs, enabled = !saving) {
            Icon(
                Icons.Filled.SaveAs,
                contentDescription = "다른 이름으로 저장",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                Icons.Filled.Save,
                contentDescription = "저장",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
