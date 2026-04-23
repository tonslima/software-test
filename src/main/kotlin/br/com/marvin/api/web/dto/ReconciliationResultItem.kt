package br.com.marvin.api.web.dto

import br.com.marvin.api.domain.vo.ReconciliationCategory
import java.math.BigDecimal
import java.util.UUID

data class ReconciliationResultItem(
    val transactionId: UUID,
    val category: ReconciliationCategory,
    val processorAmount: BigDecimal?,
    val internalAmount: BigDecimal?,
)
