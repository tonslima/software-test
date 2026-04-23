package br.com.marvin.api.application.usecase.stats

import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.exception.ReconciliationRunNotFoundException
import br.com.marvin.api.infrastructure.persistence.CategoryCount
import br.com.marvin.api.infrastructure.persistence.ReconciliationResultRepository
import br.com.marvin.api.infrastructure.persistence.ReconciliationRunRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class GetReconciliationStatsUseCase(
    private val runRepository: ReconciliationRunRepository,
    private val resultRepository: ReconciliationResultRepository,
) {

    fun execute(runId: UUID): ReconciliationStatsOutput {
        val run = runRepository.findById(runId)
            .orElseThrow { ReconciliationRunNotFoundException(runId) }

        if (run.status == RunStatus.UPLOADING || run.status == RunStatus.PENDING || run.status == RunStatus.PROCESSING) {
            return ReconciliationStatsOutput.StillProcessing(
                runId = run.id,
                runStatus = run.status,
                createdAt = run.createdAt,
            )
        }

        val counts = resultRepository.countByRunIdGroupByCategory(runId)
        val categories = buildCategoryMap(counts)
        val total = categories.values.sum()
        val discrepancyRate = calculateDiscrepancyRate(categories, total)

        return ReconciliationStatsOutput.Done(
            runId = run.id,
            runStatus = run.status,
            totalTransactions = total,
            discrepancyRate = discrepancyRate,
            categories = categories,
        )
    }

    private fun buildCategoryMap(counts: List<CategoryCount>): Map<ReconciliationCategory, Long> {
        val map = ReconciliationCategory.entries.associateWith { 0L }.toMutableMap()
        counts.forEach { map[it.category] = it.count }
        return map
    }

    private fun calculateDiscrepancyRate(categories: Map<ReconciliationCategory, Long>, total: Long): Double {
        if (total == 0L) return 0.0
        val matched = categories.getValue(ReconciliationCategory.MATCHED)

        val discrepancies = BigDecimal(total - matched)
        val rate = discrepancies.divide(BigDecimal(total), 4, RoundingMode.HALF_UP) * BigDecimal(100)
        return rate.setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}
