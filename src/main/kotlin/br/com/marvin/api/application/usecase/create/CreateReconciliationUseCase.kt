package br.com.marvin.api.application.usecase.create

import br.com.marvin.api.application.port.FileStorage
import br.com.marvin.api.application.port.ReconciliationEventPublisher
import br.com.marvin.api.domain.model.ReconciliationRun
import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.infrastructure.persistence.ReconciliationRunRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID

@Service
class CreateReconciliationUseCase(
    private val fileStorage: FileStorage,
    private val runRepository: ReconciliationRunRepository,
    private val eventPublisher: ReconciliationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(referenceDate: LocalDate, fileInputStream: InputStream, fileSize: Long): UUID {
        val run = createRun(referenceDate)

        log.info("Upload started for runId={}, size={}bytes", run.id, fileSize)
        try {
            fileStorage.upload(run.s3Key, fileInputStream, fileSize)
        } catch (ex: Exception) {
            log.error("Upload failed for runId={}", run.id, ex)
            markAsFailed(run)
            throw ex
        }
        log.info("Upload completed for runId={}", run.id)

        confirmUpload(run)
        eventPublisher.publish(run.id)
        return run.id
    }

    private fun createRun(referenceDate: LocalDate): ReconciliationRun = transactionTemplate.execute {
        val runId = UUID.randomUUID()
        runRepository.save(
            ReconciliationRun(
                id = runId,
                referenceDate = referenceDate,
                s3Key = "reconciliations/runs/$runId/input.csv",
            )
        )
    }

    private fun markAsFailed(run: ReconciliationRun): ReconciliationRun = transactionTemplate.execute {
        run.status = RunStatus.FAILED
        runRepository.save(run)
    }

    private fun confirmUpload(run: ReconciliationRun): ReconciliationRun = transactionTemplate.execute {
        run.status = RunStatus.PENDING
        runRepository.save(run)
    }

}
