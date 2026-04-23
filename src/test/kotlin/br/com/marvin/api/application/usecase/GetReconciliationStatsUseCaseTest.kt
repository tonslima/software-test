package br.com.marvin.api.application.usecase

import br.com.marvin.api.domain.model.ReconciliationRun
import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.exception.ReconciliationRunNotFoundException
import br.com.marvin.api.infrastructure.persistence.CategoryCount
import br.com.marvin.api.infrastructure.persistence.ReconciliationResultRepository
import br.com.marvin.api.infrastructure.persistence.ReconciliationRunRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetReconciliationStatsUseCaseTest {

    private val runRepository = mock<ReconciliationRunRepository>()
    private val resultRepository = mock<ReconciliationResultRepository>()

    private val useCase = GetReconciliationStatsUseCase(runRepository, resultRepository)

    @Test
    fun `should throw when run does not exist`() {
        val runId = UUID.randomUUID()
        whenever(runRepository.findById(runId)).thenReturn(Optional.empty())

        assertThrows<ReconciliationRunNotFoundException> {
            useCase.execute(runId)
        }
    }

    @Test
    fun `should return StillProcessing when run is PENDING`() {
        val run = createRun(RunStatus.PENDING)
        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))

        val output = useCase.execute(run.id)

        assertIs<ReconciliationStatsOutput.StillProcessing>(output)
        assertEquals(RunStatus.PENDING, output.runStatus)
        verify(resultRepository, never()).countByRunIdGroupByCategory(run.id)
    }

    @Test
    fun `should return StillProcessing when run is PROCESSING`() {
        val run = createRun(RunStatus.PROCESSING)
        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))

        val output = useCase.execute(run.id)

        assertIs<ReconciliationStatsOutput.StillProcessing>(output)
        assertEquals(RunStatus.PROCESSING, output.runStatus)
    }

    @Test
    fun `should return Done with stats when run is COMPLETED`() {
        val run = createRun(RunStatus.COMPLETED)
        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))
        whenever(resultRepository.countByRunIdGroupByCategory(run.id)).thenReturn(
            listOf(
                categoryCount(ReconciliationCategory.MATCHED, 14L),
                categoryCount(ReconciliationCategory.MISMATCHED, 1L),
                categoryCount(ReconciliationCategory.UNRECONCILED_INTERNAL, 5L),
            )
        )

        val output = useCase.execute(run.id)

        assertIs<ReconciliationStatsOutput.Done>(output)
        assertEquals(RunStatus.COMPLETED, output.runStatus)
        assertEquals(20, output.totalTransactions)
        assertEquals(30.0, output.discrepancyRate)
        assertEquals(14L, output.categories[ReconciliationCategory.MATCHED])
        assertEquals(1L, output.categories[ReconciliationCategory.MISMATCHED])
        assertEquals(0L, output.categories[ReconciliationCategory.UNRECONCILED_PROCESSOR])
        assertEquals(5L, output.categories[ReconciliationCategory.UNRECONCILED_INTERNAL])
    }

    @Test
    fun `should return Done with all zeros when run is FAILED`() {
        val run = createRun(RunStatus.FAILED)
        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))
        whenever(resultRepository.countByRunIdGroupByCategory(run.id)).thenReturn(emptyList())

        val output = useCase.execute(run.id)

        assertIs<ReconciliationStatsOutput.Done>(output)
        assertEquals(RunStatus.FAILED, output.runStatus)
        assertEquals(0, output.totalTransactions)
        assertEquals(0.0, output.discrepancyRate)
        assertEquals(0L, output.categories[ReconciliationCategory.MATCHED])
    }

    @Test
    fun `should calculate discrepancy rate correctly with only matched`() {
        val run = createRun(RunStatus.COMPLETED)
        whenever(runRepository.findById(run.id)).thenReturn(Optional.of(run))
        whenever(resultRepository.countByRunIdGroupByCategory(run.id)).thenReturn(
            listOf(categoryCount(ReconciliationCategory.MATCHED, 100L))
        )

        val output = useCase.execute(run.id)

        assertIs<ReconciliationStatsOutput.Done>(output)
        assertEquals(0.0, output.discrepancyRate)
    }

    private fun categoryCount(category: ReconciliationCategory, count: Long): CategoryCount =
        object : CategoryCount {
            override val category = category
            override val count = count
        }

    private fun createRun(status: RunStatus): ReconciliationRun {
        val run = ReconciliationRun(
            id = UUID.randomUUID(),
            referenceDate = LocalDate.now().minusDays(1),
            s3Key = "reconciliations/runs/test/input.csv",
        )
        run.status = status
        return run
    }
}
