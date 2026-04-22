package br.com.marvin.api.infrastructure.persistence

import br.com.marvin.api.domain.model.InternalTransaction
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface InternalTransactionRepository : JpaRepository<InternalTransaction, UUID> {

    fun findByCreatedAtBetween(start: Instant, end: Instant): List<InternalTransaction>
}
