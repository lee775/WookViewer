package com.wook.viewer.domain.model

/**
 * 문서 내부 목차의 한 노드 (PDF의 Document Outline / 북마크 트리).
 *
 * @param title 사용자에게 보일 제목
 * @param pageIndex 0-기반 페이지 인덱스. null이면 페이지 점프 불가 (헤더 전용).
 * @param children 하위 노드. 빈 리스트면 leaf.
 */
data class OutlineNode(
    val title: String,
    val pageIndex: Int?,
    val children: List<OutlineNode> = emptyList()
)
