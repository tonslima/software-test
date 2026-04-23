package br.com.marvin.api.application.usecase

import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.vo.RunStatus
import java.time.Instant
import java.util.UUID

sealed class ReconciliationStatsOutput {
    data class StillProcessing(
        val runId: UUID,
        val runStatus: RunStatus,
        val createdAt: Instant,
    ) : ReconciliationStatsOutput()

    data class Done(
        val runId: UUID,
        val runStatus: RunStatus,
        val totalTransactions: Long,
        val discrepancyRate: Double,
        val categories: Map<ReconciliationCategory, Long>,
    ) : ReconciliationStatsOutput()
}
