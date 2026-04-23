package br.com.marvin.api.application.usecase

import br.com.marvin.api.domain.model.ReconciliationResult
import br.com.marvin.api.domain.vo.RunStatus
import org.springframework.data.domain.Page
import java.time.Instant
import java.util.UUID

sealed class ReconciliationResultsOutput {
    data class StillProcessing(
        val runId: UUID,
        val runStatus: RunStatus,
        val createdAt: Instant,
    ) : ReconciliationResultsOutput()
    data class Done(
        val runStatus: RunStatus,
        val errorMessage: String?,
        val results: Page<ReconciliationResult>,
    ) : ReconciliationResultsOutput()
}
