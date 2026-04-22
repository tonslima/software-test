package br.com.marvin.api.application

import br.com.marvin.api.domain.model.InternalTransaction
import br.com.marvin.api.domain.vo.ReconciliationCategory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID


@Component
class ReconciliationMatcher {

    fun match(
        csvTransaction: CsvTransaction,
        internalTransactionsMap: Map<UUID, InternalTransaction>,
    ): MatchResult {
        val internal = internalTransactionsMap[csvTransaction.transactionId] ?: return MatchResult(
            category = ReconciliationCategory.UNRECONCILED_PROCESSOR,
            processorAmount = csvTransaction.amount,
            internalAmount = null,
        )

        val difference = (csvTransaction.amount - internal.amount).abs()

//        if (csvTransaction.amount in internal.amount - TOLERANCE..internal.amount + TOLERANCE)

        if (difference <= TOLERANCE) {
            return MatchResult(
                category = ReconciliationCategory.MATCHED,
                processorAmount = csvTransaction.amount,
                internalAmount = internal.amount,
            )
        }

        return MatchResult(
            category = ReconciliationCategory.MISMATCHED,
            processorAmount = csvTransaction.amount,
            internalAmount = internal.amount,
        )
    }

    companion object {
        val TOLERANCE: BigDecimal = BigDecimal("0.01")
    }
}
