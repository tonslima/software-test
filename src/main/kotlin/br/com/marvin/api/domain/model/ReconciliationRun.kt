package br.com.marvin.api.domain.model

import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.exception.ReferenceDateException

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Entity
@Table(name = "reconciliation_runs")
class ReconciliationRun(
    @Id
    val id: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RunStatus = RunStatus.UPLOADING,

    @Column(name = "reference_date", nullable = false)
    val referenceDate: LocalDate,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "s3_key", nullable = false)
    val s3Key: String,

    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null,
) {
    init {
        val today = LocalDate.now(ZoneOffset.UTC)
        if (referenceDate.isAfter(today)) {
            throw ReferenceDateException("referenceDate cannot be in the future")
        }
        if (referenceDate.isBefore(today.minusDays(90))) {
            throw ReferenceDateException("referenceDate cannot be older than 90 days")
        }
    }
}

