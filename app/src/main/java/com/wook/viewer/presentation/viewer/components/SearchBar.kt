package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 뷰어 상단 검색 모드 — 입력 + 매치 카운터 + 이전/다음 + 닫기.
 */
@Composable
fun SearchBar(
    query: String,
    matchCount: Int,
    currentMatchIndex: Int,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(40.dp)
                .padding(8.dp)
        )

        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("문서 안에서 검색", fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
        )

        if (query.isNotEmpty()) {
            val counter = when {
                searching -> "…"
                matchCount == 0 -> "0"
                else -> "${currentMatchIndex + 1} / $matchCount"
            }
            Text(
                text = counter,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = onPrev, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "이전 매치")
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "다음 매치")
            }
        }

        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "검색 닫기")
        }
    }
}
