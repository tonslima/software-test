package br.com.marvin.api.web

import br.com.marvin.api.application.usecase.ReconciliationResultsOutput
import br.com.marvin.api.web.dto.ReconciliationResultItem
import br.com.marvin.api.web.dto.ReconciliationResultResponse
import br.com.marvin.api.web.dto.ReconciliationRunStatusResponse

object ReconciliationResultMapper {

    fun toStatusResponse(output: ReconciliationResultsOutput.StillProcessing): ReconciliationRunStatusResponse =
        ReconciliationRunStatusResponse(
            runId = output.runId,
            runStatus = output.runStatus,
            createdAt = output.createdAt,
        )

    fun toResultResponse(output: ReconciliationResultsOutput.Done): ReconciliationResultResponse =
        ReconciliationResultResponse(
            runStatus = output.runStatus,
            errorMessage = output.errorMessage,
            page = output.results.number,
            size = output.results.size,
            totalElements = output.results.totalElements,
            totalPages = output.results.totalPages,
            results = output.results.content.map { result ->
                ReconciliationResultItem(
                    transactionId = result.transactionId,
                    category = result.category,
                    processorAmount = result.processorAmount,
                    internalAmount = result.internalAmount,
                )
            },
        )
}
