package br.com.marvin.api.application.port

import java.util.UUID

interface ReconciliationEventPublisher {
    fun publish(runId: UUID)
}
