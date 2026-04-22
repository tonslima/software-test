package br.com.marvin.api.application

import java.math.BigDecimal
import java.util.UUID

data class CsvTransaction(
    val transactionId: UUID,
    val merchantId: String,
    val amount: BigDecimal,
    val currency: String,
    val settledAt: String,
    val processorReference: String,
    val status: String,
)
