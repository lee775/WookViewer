package com.wook.viewer.domain.usecase

import com.wook.viewer.domain.model.RecentDocument
import com.wook.viewer.domain.model.RecentSortOrder
import com.wook.viewer.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecentDocumentsUseCase @Inject constructor(
    private val repo: DocumentRepository
) {
    operator fun invoke(order: RecentSortOrder = RecentSortOrder.RECENT): Flow<List<RecentDocument>> =
        repo.observeRecent(order)
}
