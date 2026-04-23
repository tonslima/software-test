package br.com.marvin.api.infrastructure.csv

import br.com.marvin.api.application.usecase.process.CsvTransaction
import br.com.marvin.api.application.port.CsvParser
import br.com.marvin.api.exception.CsvParseException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Component
import java.io.InputStream
import java.math.BigDecimal
import java.util.UUID

@Component
class ApacheCsvParser : CsvParser {

    override fun parse(inputStream: InputStream): Sequence<CsvTransaction> {
        val reader = inputStream.bufferedReader()
        val format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
        val csvParser = CSVParser(reader, format)

        validateHeader(csvParser.headerNames)

        val seen = mutableSetOf<UUID>()

        return csvParser.asSequence()
            .map { record -> parseRecord(record) }
            .filter { seen.add(it.transactionId) }
    }

    private fun validateHeader(actual: List<String>) {
        val normalized = actual.map { it.trim().lowercase() }
        if (normalized != EXPECTED_HEADERS) {
            throw CsvParseException("Invalid CSV header: expected $EXPECTED_HEADERS, got $normalized")
        }
    }

    private fun parseRecord(record: CSVRecord): CsvTransaction {
        return try {
            CsvTransaction(
                transactionId = UUID.fromString(record.get(TRANSACTION_ID_HEADER).trim()),
                merchantId = record.get(MERCHANT_ID_HEADER).trim(),
                amount = BigDecimal(record.get(AMOUNT_HEADER).trim()),
                currency = record.get(CURRENCY_HEADER).trim(),
                settledAt = record.get(SETTLED_AT_HEADER).trim(),
                processorReference = record.get(PROCESSOR_REFERENCE_HEADER).trim(),
                status = record.get(STATUS_HEADER).trim(),
            )
        } catch (ex: Exception) {
            throw CsvParseException("Malformed record at line ${record.recordNumber}: ${ex.message}")
        }
    }

    companion object {
        private const val TRANSACTION_ID_HEADER = "transaction_id"
        private const val MERCHANT_ID_HEADER = "merchant_id"
        private const val AMOUNT_HEADER = "amount"
        private const val CURRENCY_HEADER = "currency"
        private const val SETTLED_AT_HEADER = "settled_at"
        private const val PROCESSOR_REFERENCE_HEADER = "processor_reference"
        private const val STATUS_HEADER = "status"

        private val EXPECTED_HEADERS = listOf(
            TRANSACTION_ID_HEADER,
            MERCHANT_ID_HEADER,
            AMOUNT_HEADER,
            CURRENCY_HEADER,
            SETTLED_AT_HEADER,
            PROCESSOR_REFERENCE_HEADER,
            STATUS_HEADER,
        )
    }
}
