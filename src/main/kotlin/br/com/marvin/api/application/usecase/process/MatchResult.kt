package br.com.marvin.api.application.usecase.process

import br.com.marvin.api.domain.vo.ReconciliationCategory
import java.math.BigDecimal

data class MatchResult(
    val category: ReconciliationCategory,
    val processorAmount: BigDecimal?,
    val internalAmount: BigDecimal?,
)
