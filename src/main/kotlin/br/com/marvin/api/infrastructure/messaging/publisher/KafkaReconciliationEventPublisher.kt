package br.com.marvin.api.infrastructure.messaging.publisher

import br.com.marvin.api.application.port.ReconciliationEventPublisher
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class KafkaReconciliationEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) : ReconciliationEventPublisher {

    override fun publish(runId: UUID) {
        kafkaTemplate.send(TOPIC, runId.toString(), runId.toString())
    }

    companion object {
        const val TOPIC = "settlement.reconciliation.requested"
    }
}
