package br.com.marvin.api.web.dto

import br.com.marvin.api.domain.vo.RunStatus

data class ReconciliationResultResponse(
    val runStatus: RunStatus,
    val errorMessage: String?,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val results: List<ReconciliationResultItem>,
)
