package br.com.marvin.api.web.dto

import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.vo.RunStatus
import java.time.Instant
import java.util.UUID

data class ReconciliationStatsResponse(
    val runId: UUID,
    val runStatus: RunStatus,
    val finishedAt: Instant?,
    val totalTransactions: Long,
    val discrepancyRate: Double,
    val categories: Map<ReconciliationCategory, Long>,
)
