package br.com.marvin.api.application.port

import java.util.UUID

interface AlertEventPublisher {
    fun publish(runId: UUID, discrepancyPercentage: Double)
}
