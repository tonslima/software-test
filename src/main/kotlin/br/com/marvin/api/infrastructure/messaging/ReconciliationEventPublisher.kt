package br.com.marvin.api.infrastructure.messaging

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ReconciliationEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {

    companion object {
        const val TOPIC = "settlement.reconciliation.requested"
    }

    fun publish(runId: UUID) {
        kafkaTemplate.send(TOPIC, runId.toString(), runId.toString())
    }
}
