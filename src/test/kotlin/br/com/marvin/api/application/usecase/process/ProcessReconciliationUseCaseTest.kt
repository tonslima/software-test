package br.com.marvin.api.application.usecase.process

import br.com.marvin.api.application.port.AlertEventPublisher
import br.com.marvin.api.application.port.CsvParser
import br.com.marvin.api.application.port.FileStorage
import br.com.marvin.api.domain.model.InternalTransaction
import br.com.marvin.api.domain.model.ReconciliationResult
import br.com.marvin.api.domain.model.ReconciliationRun
import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.infrastructure.persistence.InternalTransactionRepository
import br.com.marvin.api.infrastructure.persistence.ReconciliationResultRepository
import br.com.marvin.api.infrastructure.persistence.ReconciliationRunRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProcessReconciliationUseCaseTest {

    private val runRepository = mock<ReconciliationRunRepository>()
    private val resultRepository = mock<ReconciliationResultRepository>()
    private val internalTransactionRepository = mock<InternalTransactionRepository>()
    private val fileStorage = mock<FileStorage>()
    private val csvParser = mock<CsvParser>()
    private val alertEventPublisher = mock<AlertEventPublisher>()
    private val transactionTemplate = mock<TransactionTemplate>()

    private val matcher = ReconciliationMatcher()

    private val useCase = ProcessReconciliationUseCase(
        runRepository, resultRepository, internalTransactionRepository,
        fileStorage, csvParser, matcher, alertEventPublisher, transactionTemplate,
    )

    private val runId = UUID.randomUUID()
    private val referenceDate = LocalDate.of(2026, 3, 15)

    private val run = ReconciliationRun(
        id = runId,
        referenceDate = referenceDate,
        s3Key = "reconciliations/runs/$runId/input.csv",
    )

    private val matchedTxId = UUID.randomUUID()
    private val mismatchedTxId = UUID.randomUUID()
    private val unreconciledInternalTxId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        whenever(runRepository.findById(runId)).thenReturn(Optional.of(run))
        whenever(runRepository.getReferenceById(runId)).thenReturn(run)
        whenever(runRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(resultRepository.saveAll(any<List<ReconciliationResult>>())).thenAnswer { it.arguments[0] }

        whenever(transactionTemplate.execute<Unit>(any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<TransactionCallback<Unit>>(0)
            callback.doInTransaction(mock())
        }
    }

    private fun setupInternalTransactions(vararg transactions: InternalTransaction) {
        whenever(internalTransactionRepository.findByCreatedAtBetween(any(), any()))
            .thenReturn(transactions.toList())
    }

    private fun setupCsvParsing(vararg transactions: CsvTransaction) {
        whenever(fileStorage.download(any())).thenReturn("dummy".byteInputStream())
        whenever(csvParser.parse(any())).thenReturn(transactions.asSequence())
    }

    private fun buildInternal(id: UUID, amount: BigDecimal) = InternalTransaction(
        transactionId = id,
        merchantId = "MERCH_001",
        amount = amount,
        currency = "BRL",
        createdAt = Instant.parse("2026-03-15T10:00:00Z"),
        status = "COMPLETED",
    )

    private fun buildCsv(id: UUID, amount: BigDecimal) = CsvTransaction(
        transactionId = id,
        merchantId = "MERCH_001",
        amount = amount,
        currency = "BRL",
        settledAt = "2026-03-15T10:00:00Z",
        processorReference = "PS-001",
        status = "SETTLED",
    )

    @Test
    fun `happy path — processes CSV and persists results with correct categories`() {
        val matchedInternal = buildInternal(matchedTxId, BigDecimal("250.00"))
        val mismatchedInternal = buildInternal(mismatchedTxId, BigDecimal("1050.00"))
        val unreconciledInternal = buildInternal(unreconciledInternalTxId, BigDecimal("310.00"))

        setupInternalTransactions(matchedInternal, mismatchedInternal, unreconciledInternal)
        setupCsvParsing(
            buildCsv(matchedTxId, BigDecimal("250.00")),
            buildCsv(mismatchedTxId, BigDecimal("999.99")),
        )

        useCase.execute(runId)

        assertEquals(RunStatus.COMPLETED, run.status)

        val captor = argumentCaptor<List<ReconciliationResult>>()
        verify(resultRepository).saveAll(captor.capture())

        val results = captor.firstValue
        assertEquals(3, results.size)

        val matched = results.first { it.transactionId == matchedTxId }
        assertEquals(ReconciliationCategory.MATCHED, matched.category)

        val mismatched = results.first { it.transactionId == mismatchedTxId }
        assertEquals(ReconciliationCategory.MISMATCHED, mismatched.category)

        val unreconciled = results.first { it.transactionId == unreconciledInternalTxId }
        assertEquals(ReconciliationCategory.UNRECONCILED_INTERNAL, unreconciled.category)
    }

    @Test
    fun `marks run as FAILED when processing throws`() {
        whenever(fileStorage.download(any())).thenThrow(RuntimeException("S3 down"))

        setupInternalTransactions()
        whenever(transactionTemplate.execute<Unit>(any())).thenAnswer { invocation ->
            val callback = invocation.getArgument<TransactionCallback<Unit>>(0)
            callback.doInTransaction(mock())
        }

        useCase.execute(runId)

        assertEquals(RunStatus.FAILED, run.status)
        assertNotNull(run.errorMessage)
        assertTrue(run.errorMessage!!.contains("S3 down"))
    }

    @Test
    fun `publishes alert when discrepancy exceeds 5 percent`() {
        setupInternalTransactions()
        setupCsvParsing(buildCsv(UUID.randomUUID(), BigDecimal("100.00")))

        whenever(resultRepository.countByRunId(runId)).thenReturn(10L)
        whenever(resultRepository.countByRunIdAndCategoryIn(eq(runId), any())).thenReturn(2L)

        useCase.execute(runId)

        verify(alertEventPublisher).publish(eq(runId), eq(20.0))
    }

    @Test
    fun `does not publish alert when discrepancy is below 5 percent`() {
        val txId = UUID.randomUUID()
        val internal = buildInternal(txId, BigDecimal("100.00"))

        setupInternalTransactions(internal)
        setupCsvParsing(buildCsv(txId, BigDecimal("100.00")))

        whenever(resultRepository.countByRunId(runId)).thenReturn(100L)
        whenever(resultRepository.countByRunIdAndCategoryIn(eq(runId), any())).thenReturn(3L)

        useCase.execute(runId)

        verify(alertEventPublisher, never()).publish(any(), any())
    }

    @Test
    fun `does not publish alert when no results`() {
        setupInternalTransactions()
        setupCsvParsing()

        whenever(resultRepository.countByRunId(runId)).thenReturn(0L)

        useCase.execute(runId)

        verify(alertEventPublisher, never()).publish(any(), any())
    }

    @Test
    fun `transitions status to PROCESSING then COMPLETED`() {
        setupInternalTransactions()
        setupCsvParsing()
        whenever(resultRepository.countByRunId(runId)).thenReturn(0L)

        useCase.execute(runId)

        verify(runRepository, org.mockito.kotlin.times(2)).save(any())
        assertEquals(RunStatus.COMPLETED, run.status)
    }
}
