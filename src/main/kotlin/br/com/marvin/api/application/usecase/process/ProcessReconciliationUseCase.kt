package br.com.marvin.api.application.usecase.process

import br.com.marvin.api.application.port.AlertEventPublisher
import br.com.marvin.api.application.port.CsvParser
import br.com.marvin.api.application.port.FileStorage
import br.com.marvin.api.domain.model.InternalTransaction
import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.model.ReconciliationResult
import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.exception.ReconciliationRunNotFoundException
import br.com.marvin.api.infrastructure.persistence.InternalTransactionRepository
import br.com.marvin.api.infrastructure.persistence.ReconciliationResultRepository
import br.com.marvin.api.infrastructure.persistence.ReconciliationRunRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class ProcessReconciliationUseCase(
    private val runRepository: ReconciliationRunRepository,
    private val resultRepository: ReconciliationResultRepository,
    private val internalTransactionRepository: InternalTransactionRepository,
    private val fileStorage: FileStorage,
    private val csvParser: CsvParser,
    private val matcher: ReconciliationMatcher,
    private val alertEventPublisher: AlertEventPublisher,
    private val transactionTemplate: TransactionTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(runId: UUID) {
        val run = runRepository.findById(runId).orElse(null)
            ?: throw ReconciliationRunNotFoundException(runId)

        log.info("Starting reconciliation for runId={}", runId)
        val startedAt = System.currentTimeMillis()

        try {
            transactionTemplate.execute {
                processReconciliation(runId, run.s3Key, run.referenceDate)
            }
            run.status = RunStatus.COMPLETED
            run.finishedAt = Instant.now()
            runRepository.save(run)

            val elapsed = System.currentTimeMillis() - startedAt
            val total = resultRepository.countByRunId(runId)
            log.info("Reconciliation completed for runId={} in {}ms — {} results", runId, elapsed, total)

            checkAlertThreshold(runId, total)
        } catch (ex: Exception) {
            log.error("Reconciliation failed for runId={}", runId, ex)
            run.status = RunStatus.FAILED
            run.finishedAt = Instant.now()
            run.errorMessage = ex.message?.take(1000)
            runRepository.save(run)
        }
    }

    private fun processReconciliation(runId: UUID, s3Key: String, referenceDate: LocalDate) {
        val run = runRepository.getReferenceById(runId)

        val internalTransactionsMap = loadInternalTransactions(referenceDate).toMutableMap()
        val batch = mutableListOf<ReconciliationResult>()

        fileStorage.download(s3Key).use { inputStream ->
            csvParser.parse(inputStream).forEach { csvTransaction ->
                val matchResult = matcher.match(csvTransaction, internalTransactionsMap)

                if (matchResult.category == ReconciliationCategory.MATCHED ||
                    matchResult.category == ReconciliationCategory.MISMATCHED
                ) {
                    internalTransactionsMap.remove(csvTransaction.transactionId)
                }

                batch.add(
                    ReconciliationResult(
                        run = run,
                        transactionId = csvTransaction.transactionId,
                        category = matchResult.category,
                        processorAmount = matchResult.processorAmount,
                        internalAmount = matchResult.internalAmount,
                    )
                )

                if (batch.size >= BATCH_SIZE) {
                    resultRepository.saveAll(batch)
                    batch.clear()
                }
            }
        }

        internalTransactionsMap
            .forEach { (_, internal) ->
                batch.add(
                    ReconciliationResult(
                        run = run,
                        transactionId = internal.transactionId,
                        category = ReconciliationCategory.UNRECONCILED_INTERNAL,
                        processorAmount = null,
                        internalAmount = internal.amount,
                    )
                )

                if (batch.size >= BATCH_SIZE) {
                    resultRepository.saveAll(batch)
                    batch.clear()
                }
            }

        if (batch.isNotEmpty()) {
            resultRepository.saveAll(batch)
        }
    }

    private fun loadInternalTransactions(referenceDate: LocalDate): Map<UUID, InternalTransaction> =
        internalTransactionRepository.findByCreatedAtBetween(
            referenceDate.minusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC),
            referenceDate.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC),
        ).associateBy { it.transactionId }

    private fun checkAlertThreshold(runId: UUID, total: Long) {
        if (total == 0L) return

        val discrepancies = resultRepository.countByRunIdAndCategoryIn(
            runId,
            listOf(
                ReconciliationCategory.MISMATCHED,
                ReconciliationCategory.UNRECONCILED_PROCESSOR,
                ReconciliationCategory.UNRECONCILED_INTERNAL,
            ),
        )

        val percentage = discrepancies.toDouble() / total
        if (percentage > ALERT_THRESHOLD) {
            log.warn("Discrepancy alert for runId={}: {}%", runId, "%.2f".format(percentage * 100))
            alertEventPublisher.publish(runId, percentage * 100)
        }
    }

    companion object {
        private const val ALERT_THRESHOLD = 0.05
        private const val BATCH_SIZE = 500
    }
}
