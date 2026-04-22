package br.com.marvin.api.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.domain.Persistable
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "reconciliation_results")
class ReconciliationResult(
    @Id
    @Column(name = "id")
    private val _id: UUID = UUID.randomUUID(),

    @ManyToOne
    @JoinColumn(name = "run_id", nullable = false)
    val run: ReconciliationRun,

    @Column(name = "transaction_id", nullable = false)
    val transactionId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val category: ReconciliationCategory,

    @Column(name = "processor_amount")
    val processorAmount: BigDecimal? = null,

    @Column(name = "internal_amount")
    val internalAmount: BigDecimal? = null,
) : Persistable<UUID> {
    override fun getId() = _id
    override fun isNew() = true
}

enum class ReconciliationCategory {
    MATCHED,
    MISMATCHED,
    UNRECONCILED_PROCESSOR,
    UNRECONCILED_INTERNAL,
}
