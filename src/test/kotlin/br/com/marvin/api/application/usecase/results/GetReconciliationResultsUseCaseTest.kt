package br.com.marvin.api.application.usecase.results

import br.com.marvin.api.domain.model.ReconciliationResult
import br.com.marvin.api.domain.model.ReconciliationRun
import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.exception.PageSizeException
import br.com.marvin.api.exception.ReconciliationRunNotFoundException
import br.com.marvin.api.infrastructure.persistence.ReconciliationResultRepository
import br.com.marvin.api.infrastructure.persistence.ReconciliationRunRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class GetReconciliationResultsUseCaseTest {

    private val runRepository = mock<ReconciliationRunRepository>()
    private val resultRepository = mock<ReconciliationResultRepository>()

    private val useCase = GetReconciliationResultsUseCase(runRepository, resultRepository)

    @Test
    fun `should throw when run does not exist`() {
        val runId = UUID.randomUUID()
        whenever(runRepository.findById(runId)).thenReturn(Optional.empty())

        assertThrows<ReconciliationRunNotFoundException> {
            useCase.execute(runId, null, 0, 50)
        }
    }

    @Test
    fun `should return StillProcessing when run is UPLOADING`() {
        val run = createRun(RunStatus.UPLOADING)
        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))

        val output = useCase.execute(run.id, null, 0, 50)

        assertIs<ReconciliationResultsOutput.StillProcessing>(output)
        assertEquals(RunStatus.UPLOADING, output.runStatus)
        verify(resultRepository, never()).findByRunId(any(), any())
    }

    @Test
    fun `should return StillProcessing when run is PENDING`() {
        val run = createRun(RunStatus.PENDING)
        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))

        val output = useCase.execute(run.id, null, 0, 50)

        assertIs<ReconciliationResultsOutput.StillProcessing>(output)
        assertEquals(RunStatus.PENDING, output.runStatus)
        verify(resultRepository, never()).findByRunId(any(), any())
    }

    @Test
    fun `should return StillProcessing when run is PROCESSING`() {
        val run = createRun(RunStatus.PROCESSING)
        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))

        val output = useCase.execute(run.id, null, 0, 50)

        assertIs<ReconciliationResultsOutput.StillProcessing>(output)
        assertEquals(RunStatus.PROCESSING, output.runStatus)
    }

    @Test
    fun `should return Done with results when run is COMPLETED`() {
        val run = createRun(RunStatus.COMPLETED)
        val result = createResult(run, ReconciliationCategory.MATCHED)
        val page = PageImpl(listOf(result), PageRequest.of(0, 50), 1)

        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))
        whenever(resultRepository.findByRunId(eq(run.id), any())).thenReturn(page)

        val output = useCase.execute(run.id, null, 0, 50)

        assertIs<ReconciliationResultsOutput.Done>(output)
        assertEquals(RunStatus.COMPLETED, output.runStatus)
        assertEquals(1, output.results.totalElements)
    }

    @Test
    fun `should return Done with empty results and errorMessage when run is FAILED`() {
        val run = createRun(RunStatus.FAILED, errorMessage = "Something went wrong")
        val page = PageImpl<ReconciliationResult>(emptyList(), PageRequest.of(0, 50), 0)

        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))
        whenever(resultRepository.findByRunId(eq(run.id), any())).thenReturn(page)

        val output = useCase.execute(run.id, null, 0, 50)

        assertIs<ReconciliationResultsOutput.Done>(output)
        assertEquals(RunStatus.FAILED, output.runStatus)
        assertEquals("Something went wrong", output.errorMessage)
        assertEquals(0, output.results.totalElements)
    }

    @Test
    fun `should filter by categories when provided`() {
        val run = createRun(RunStatus.COMPLETED)
        val categories = listOf(ReconciliationCategory.MISMATCHED, ReconciliationCategory.UNRECONCILED_PROCESSOR)
        val page = PageImpl<ReconciliationResult>(emptyList(), PageRequest.of(0, 50), 0)

        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))
        whenever(resultRepository.findByRunIdAndCategoryIn(eq(run.id), eq(categories), any())).thenReturn(page)

        val output = useCase.execute(run.id, categories, 0, 50)

        assertIs<ReconciliationResultsOutput.Done>(output)
        verify(resultRepository).findByRunIdAndCategoryIn(eq(run.id), eq(categories), any())
        verify(resultRepository, never()).findByRunId(any(), any())
    }

    @Test
    fun `should throw when page size exceeds maximum`() {
        assertThrows<PageSizeException> {
            useCase.execute(UUID.randomUUID(), null, 0, 201)
        }
    }

    private fun createRun(
        status: RunStatus,
        errorMessage: String? = null,
    ): ReconciliationRun {
        val run = ReconciliationRun(
            id = UUID.randomUUID(),
            referenceDate = LocalDate.now().minusDays(1),
            s3Key = "reconciliations/runs/test/input.csv",
        )
        run.status = status
        run.errorMessage = errorMessage
        return run
    }

    private fun createResult(
        run: ReconciliationRun,
        category: ReconciliationCategory,
    ): ReconciliationResult = ReconciliationResult(
        run = run,
        transactionId = UUID.randomUUID(),
        category = category,
        processorAmount = BigDecimal("100.00"),
        internalAmount = BigDecimal("100.00"),
    )
}
