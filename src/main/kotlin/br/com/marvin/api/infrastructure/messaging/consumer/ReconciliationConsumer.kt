package br.com.marvin.api.infrastructure.messaging.consumer

import br.com.marvin.api.application.usecase.process.ProcessReconciliationUseCase
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ReconciliationConsumer(
    private val processReconciliationUseCase: ProcessReconciliationUseCase,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["settlement.reconciliation.requested"],
        groupId = "settlement-reconciliation",
    )
    fun consume(message: String) {
        val runId = UUID.fromString(message)
        log.info("Received reconciliation event for runId={}", runId)
        processReconciliationUseCase.execute(runId)
    }
}