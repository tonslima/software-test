package br.com.marvin.api.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "internal_transactions")
class InternalTransaction(
    @Id
    @Column(name = "transaction_id")
    val transactionId: UUID,

    @Column(name = "merchant_id", nullable = false)
    val merchantId: String,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(nullable = false, length = 20)
    val status: String,

    val description: String? = null,
)
