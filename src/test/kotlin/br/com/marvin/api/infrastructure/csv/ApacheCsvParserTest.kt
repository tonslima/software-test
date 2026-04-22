package br.com.marvin.api.infrastructure.csv

import br.com.marvin.api.exception.CsvParseException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApacheCsvParserTest {

    private val parser = ApacheCsvParser()

    private val validHeader = "transaction_id,merchant_id,amount,currency,settled_at,processor_reference,status"

    private fun csv(vararg lines: String) =
        (listOf(validHeader) + lines).joinToString("\n").byteInputStream()

    @Test
    fun `parses valid CSV row`() {
        val id = UUID.randomUUID()
        val input = csv("$id,MERCH_001,250.00,BRL,2026-03-15T10:00:00Z,PS-001,SETTLED")

        val results = parser.parse(input).toList()

        assertEquals(1, results.size)
        val row = results[0]
        assertEquals(id, row.transactionId)
        assertEquals("MERCH_001", row.merchantId)
        assertEquals(BigDecimal("250.00"), row.amount)
        assertEquals("BRL", row.currency)
        assertEquals("2026-03-15T10:00:00Z", row.settledAt)
        assertEquals("PS-001", row.processorReference)
        assertEquals("SETTLED", row.status)
    }

    @Test
    fun `parses multiple rows`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val input = csv(
            "$id1,MERCH_001,100.00,BRL,2026-03-15T10:00:00Z,PS-001,SETTLED",
            "$id2,MERCH_002,200.00,BRL,2026-03-15T11:00:00Z,PS-002,SETTLED",
        )

        val results = parser.parse(input).toList()

        assertEquals(2, results.size)
        assertEquals(id1, results[0].transactionId)
        assertEquals(id2, results[1].transactionId)
    }

    @Test
    fun `deduplicates rows with same transaction_id keeping first`() {
        val id = UUID.randomUUID()
        val input = csv(
            "$id,MERCH_001,100.00,BRL,2026-03-15T10:00:00Z,PS-001,SETTLED",
            "$id,MERCH_001,999.99,BRL,2026-03-15T10:00:00Z,PS-001,SETTLED",
        )

        val results = parser.parse(input).toList()

        assertEquals(1, results.size)
        assertEquals(BigDecimal("100.00"), results[0].amount)
    }

    @Test
    fun `returns empty sequence for CSV with only header`() {
        val input = csv()

        val results = parser.parse(input).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `throws on invalid header`() {
        val input = "wrong_header,merchant_id,amount,currency,settled_at,processor_reference,status\n"
            .byteInputStream()

        val ex = assertThrows<CsvParseException> { parser.parse(input).toList() }
        assertTrue(ex.message!!.contains("Invalid CSV header"))
    }

    @Test
    fun `throws on malformed record`() {
        val input = csv("not-a-uuid,MERCH_001,250.00,BRL,2026-03-15T10:00:00Z,PS-001,SETTLED")

        val ex = assertThrows<CsvParseException> { parser.parse(input).toList() }
        assertTrue(ex.message!!.contains("Malformed record"))
    }

    @Test
    fun `throws on invalid amount`() {
        val id = UUID.randomUUID()
        val input = csv("$id,MERCH_001,not-a-number,BRL,2026-03-15T10:00:00Z,PS-001,SETTLED")

        val ex = assertThrows<CsvParseException> { parser.parse(input).toList() }
        assertTrue(ex.message!!.contains("Malformed record"))
    }
}
