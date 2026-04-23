package br.com.marvin.api.web.dto

import br.com.marvin.api.domain.vo.RunStatus
import java.time.Instant
import java.util.UUID

data class ReconciliationRunStatusResponse(
    val runId: UUID,
    val runStatus: RunStatus,
    val createdAt: Instant,
)
