package br.com.marvin.api.application.usecase.process

import br.com.marvin.api.domain.model.InternalTransaction
import br.com.marvin.api.domain.vo.ReconciliationCategory
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReconciliationMatcherTest {

    private val matcher = ReconciliationMatcher()

    private val transactionId = UUID.randomUUID()

    private fun buildInternal(amount: BigDecimal) = InternalTransaction(
        transactionId = transactionId,
        merchantId = "MERCH_001",
        amount = amount,
        currency = "BRL",
        createdAt = Instant.now(),
        status = "COMPLETED",
    )

    private fun buildCsv(amount: BigDecimal) = CsvTransaction(
        transactionId = transactionId,
        merchantId = "MERCH_001",
        amount = amount,
        currency = "BRL",
        settledAt = "2026-03-15T10:00:00Z",
        processorReference = "PS-001",
        status = "SETTLED",
    )

    private fun mapOf(internal: InternalTransaction) =
        mapOf(internal.transactionId to internal)

    @Test
    fun `returns MATCHED when amounts are equal`() {
        val internal = buildInternal(BigDecimal("100.00"))
        val csv = buildCsv(BigDecimal("100.00"))

        val result = matcher.match(csv, mapOf(internal))

        assertEquals(ReconciliationCategory.MATCHED, result.category)
        assertEquals(csv.amount, result.processorAmount)
        assertEquals(internal.amount, result.internalAmount)
    }

    @Test
    fun `returns MATCHED when difference is exactly 0_01`() {
        val internal = buildInternal(BigDecimal("100.00"))
        val csv = buildCsv(BigDecimal("100.01"))

        val result = matcher.match(csv, mapOf(internal))

        assertEquals(ReconciliationCategory.MATCHED, result.category)
    }

    @Test
    fun `returns MATCHED when processor amount is lower by tolerance`() {
        val internal = buildInternal(BigDecimal("100.01"))
        val csv = buildCsv(BigDecimal("100.00"))

        val result = matcher.match(csv, mapOf(internal))

        assertEquals(ReconciliationCategory.MATCHED, result.category)
    }

    @Test
    fun `returns MISMATCHED when difference is 0_02`() {
        val internal = buildInternal(BigDecimal("100.00"))
        val csv = buildCsv(BigDecimal("100.02"))

        val result = matcher.match(csv, mapOf(internal))

        assertEquals(ReconciliationCategory.MISMATCHED, result.category)
        assertEquals(csv.amount, result.processorAmount)
        assertEquals(internal.amount, result.internalAmount)
    }

    @Test
    fun `returns MISMATCHED when amounts differ significantly`() {
        val internal = buildInternal(BigDecimal("1050.00"))
        val csv = buildCsv(BigDecimal("999.99"))

        val result = matcher.match(csv, mapOf(internal))

        assertEquals(ReconciliationCategory.MISMATCHED, result.category)
    }

    @Test
    fun `returns UNRECONCILED_PROCESSOR when transaction not found internally`() {
        val csv = buildCsv(BigDecimal("250.00"))

        val result = matcher.match(csv, emptyMap())

        assertEquals(ReconciliationCategory.UNRECONCILED_PROCESSOR, result.category)
        assertEquals(csv.amount, result.processorAmount)
        assertNull(result.internalAmount)
    }
}
