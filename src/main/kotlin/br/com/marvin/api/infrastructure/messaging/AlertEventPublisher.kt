package br.com.marvin.api.infrastructure.messaging

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AlertEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {

    companion object {
        const val TOPIC = "settlement.reconciliation.events"
    }

    fun publish(runId: UUID, discrepancyPercentage: Double) {
        val payload = """{"runId":"$runId","discrepancyPercentage":${"%.2f".format(discrepancyPercentage)}}"""
        kafkaTemplate.send(TOPIC, runId.toString(), payload)
    }
}
