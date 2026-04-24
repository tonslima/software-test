package br.com.marvin.api.web.dto

import br.com.marvin.api.domain.vo.RunStatus
import java.time.Instant

data class ReconciliationResultResponse(
    val runStatus: RunStatus,
    val finishedAt: Instant?,
    val errorMessage: String?,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val results: List<ReconciliationResultItem>,
)
