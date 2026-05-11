package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wook.viewer.R
import com.wook.viewer.domain.model.OutlineNode

/**
 * PDF 등 문서 내부 목차를 트리 형태로 보여주는 시트.
 *
 * 펼침/접힘 상태는 노드 식별자(depth + index 경로)로 추적.
 * 페이지 점프 가능한 노드만 클릭 시 onJumpTo 호출.
 */
@Composable
fun OutlineSheet(
    outline: List<OutlineNode>,
    onJumpTo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    // 경로 문자열 → 펼침 여부. 기본은 1단계(루트만 펼침)
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // expanded는 mutableStateMapOf — 읽기만 해도 recomposition 추적되므로 매번 펼침
    val flat = flatten(outline, parentPath = "", depth = 0, expanded = expanded)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            text = stringResource(R.string.outline_sheet_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            items(flat, key = { it.path }) { row ->
                OutlineRow(
                    row = row,
                    onToggle = { expanded[row.path] = !(expanded[row.path] ?: false) },
                    onJump = { row.node.pageIndex?.let { onJumpTo(it); onDismiss() } }
                )
            }
        }
    }
}

private data class FlatRow(
    val node: OutlineNode,
    val depth: Int,
    val path: String,
    val isExpanded: Boolean
)

private fun flatten(
    nodes: List<OutlineNode>,
    parentPath: String,
    depth: Int,
    expanded: Map<String, Boolean>
): List<FlatRow> {
    val rows = mutableListOf<FlatRow>()
    nodes.forEachIndexed { index, node ->
        val path = if (parentPath.isEmpty()) "$index" else "$parentPath/$index"
        val isExpanded = expanded[path] ?: (depth == 0)  // 루트는 기본 펼침
        rows += FlatRow(node, depth, path, isExpanded)
        if (isExpanded && node.children.isNotEmpty()) {
            rows += flatten(node.children, path, depth + 1, expanded)
        }
    }
    return rows
}

@Composable
private fun OutlineRow(
    row: FlatRow,
    onToggle: () -> Unit,
    onJump: () -> Unit
) {
    val node = row.node
    val canJump = node.pageIndex != null
    val hasChildren = node.children.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canJump || hasChildren) {
                if (canJump) onJump() else if (hasChildren) onToggle()
            }
            .padding(
                start = (12 + row.depth * 20).dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (hasChildren) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (row.isExpanded) Icons.Filled.KeyboardArrowDown
                    else Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = node.title.ifBlank { "(제목 없음)" },
            color = if (canJump) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (row.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (node.pageIndex != null) {
            Text(
                text = "${node.pageIndex + 1}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
