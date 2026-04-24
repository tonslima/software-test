package br.com.marvin.api.application.usecase.results

import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.exception.PageSizeException
import br.com.marvin.api.exception.ReconciliationRunNotFoundException
import br.com.marvin.api.infrastructure.persistence.ReconciliationResultRepository
import br.com.marvin.api.infrastructure.persistence.ReconciliationRunRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetReconciliationResultsUseCase(
    private val runRepository: ReconciliationRunRepository,
    private val resultRepository: ReconciliationResultRepository,
) {

    fun execute(
        runId: UUID,
        categories: List<ReconciliationCategory>?,
        page: Int,
        size: Int,
    ): ReconciliationResultsOutput {
        if (size > MAX_PAGE_SIZE) {
            throw PageSizeException("Page size must not exceed $MAX_PAGE_SIZE")
        }

        val run = runRepository.findById(runId).orElse(null)
            ?: throw ReconciliationRunNotFoundException(runId)

        if (run.status == RunStatus.UPLOADING || run.status == RunStatus.PENDING) {
            return ReconciliationResultsOutput.StillProcessing(
                runId = run.id,
                runStatus = run.status,
                createdAt = run.createdAt,
            )
        }

        val pageable = PageRequest.of(page, size)
        val results = if (categories.isNullOrEmpty()) {
            resultRepository.findByRunId(runId, pageable)
        } else {
            resultRepository.findByRunIdAndCategoryIn(runId, categories, pageable)
        }

        return ReconciliationResultsOutput.Done(
            runStatus = run.status,
            finishedAt = run.finishedAt,
            errorMessage = run.errorMessage,
            results = results,
        )
    }

    companion object {
        const val MAX_PAGE_SIZE = 200
    }
}
