package br.com.marvin.api.infrastructure.messaging.publisher

import br.com.marvin.api.application.port.AlertEventPublisher
import tools.jackson.databind.ObjectMapper
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class KafkaAlertEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : AlertEventPublisher {

    override fun publish(runId: UUID, discrepancyPercentage: Double) {
        val dto = AlertEvent(
            runId,
            discrepancyPercentage
        )
        val payload = objectMapper.writeValueAsString(dto)
        kafkaTemplate.send(TOPIC, runId.toString(), payload)
    }

    private data class AlertEvent(val runId: UUID, val discrepancyPercentage: Double)

    companion object {
        const val TOPIC = "settlement.reconciliation.events"
    }
}
