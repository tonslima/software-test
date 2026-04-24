package br.com.marvin.api.web.mapper

import br.com.marvin.api.application.usecase.stats.ReconciliationStatsOutput
import br.com.marvin.api.web.dto.ReconciliationRunStatusResponse
import br.com.marvin.api.web.dto.ReconciliationStatsResponse

object ReconciliationStatsMapper {

    fun toStatusResponse(output: ReconciliationStatsOutput.StillProcessing): ReconciliationRunStatusResponse =
        ReconciliationRunStatusResponse(
            runId = output.runId,
            runStatus = output.runStatus,
            createdAt = output.createdAt,
        )

    fun toStatsResponse(output: ReconciliationStatsOutput.Done): ReconciliationStatsResponse =
        ReconciliationStatsResponse(
            runId = output.runId,
            runStatus = output.runStatus,
            finishedAt = output.finishedAt,
            totalTransactions = output.totalTransactions,
            discrepancyRate = output.discrepancyRate,
            categories = output.categories,
        )
}