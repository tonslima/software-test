package br.com.marvin.api.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

class ReconciliationRunTest {

    private fun buildRun(referenceDate: LocalDate) = ReconciliationRun(
        id = UUID.randomUUID(),
        referenceDate = referenceDate,
        s3Key = "reconciliations/test.csv",
    )

    @Test
    fun `accepts today`() {
        val today = LocalDate.now(ZoneOffset.UTC)
        val run = buildRun(today)
        assertEquals(today, run.referenceDate)
    }

    @Test
    fun `accepts date exactly 90 days ago`() {
        val boundary = LocalDate.now(ZoneOffset.UTC).minusDays(90)
        val run = buildRun(boundary)
        assertEquals(boundary, run.referenceDate)
    }

    @Test
    fun `rejects future date`() {
        val tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1)
        val ex = assertThrows<ReferenceDateException> { buildRun(tomorrow) }
        assertEquals("referenceDate cannot be in the future", ex.message)
    }

    @Test
    fun `rejects date older than 90 days`() {
        val old = LocalDate.now(ZoneOffset.UTC).minusDays(91)
        val ex = assertThrows<ReferenceDateException> { buildRun(old) }
        assertEquals("referenceDate cannot be older than 90 days", ex.message)
    }
}
