package br.com.marvin.api.infrastructure.messaging.publisher

import br.com.marvin.api.application.port.AlertEventPublisher
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class KafkaAlertEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) : AlertEventPublisher {

    companion object {
        const val TOPIC = "settlement.reconciliation.events"
    }

    override fun publish(runId: UUID, discrepancyPercentage: Double) {
        val payload = """{"runId":"$runId","discrepancyPercentage":${"%.2f".format(discrepancyPercentage)}}"""
        kafkaTemplate.send(TOPIC, runId.toString(), payload)
    }
}