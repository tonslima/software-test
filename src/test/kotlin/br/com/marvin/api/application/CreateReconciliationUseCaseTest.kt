package br.com.marvin.api.application

import br.com.marvin.api.application.port.FileStorage
import br.com.marvin.api.application.port.ReconciliationEventPublisher
import br.com.marvin.api.application.usecase.create.CreateReconciliationUseCase
import br.com.marvin.api.domain.model.ReconciliationRun
import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.infrastructure.persistence.ReconciliationRunRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateReconciliationUseCaseTest {

    private val fileStorage = mock<FileStorage>()
    private val runRepository = mock<ReconciliationRunRepository>()
    private val eventPublisher = mock<ReconciliationEventPublisher>()
    private val transactionTemplate = mock<TransactionTemplate>()

    private val useCase = CreateReconciliationUseCase(fileStorage, runRepository, eventPublisher, transactionTemplate)

    private val date = LocalDate.now(ZoneOffset.UTC).minusDays(1)
    private val content = "header\nrow1".toByteArray()

    @BeforeEach
    fun setup() {
        whenever(runRepository.save(any())).thenAnswer { it.arguments[0] as ReconciliationRun }
        whenever(transactionTemplate.execute<ReconciliationRun>(any())).thenAnswer { invocation ->
            val action = invocation.getArgument<org.springframework.transaction.support.TransactionCallback<ReconciliationRun>>(0)
            action.doInTransaction(mock())
        }
    }

    @Test
    fun `persists run, uploads file, transitions to PENDING and publishes event`() {
        val runId = useCase.execute(date, ByteArrayInputStream(content), content.size.toLong())

        assertNotNull(runId)

        val runCaptor = argumentCaptor<ReconciliationRun>()
        verify(runRepository, times(2)).save(runCaptor.capture())

        assertEquals(RunStatus.PENDING, runCaptor.lastValue.status)
        assertEquals(date, runCaptor.lastValue.referenceDate)
        assertTrue(runCaptor.lastValue.s3Key.contains(runId.toString()))

        verify(fileStorage).upload(any(), any(), any())
        verify(eventPublisher).publish(runId)
    }

    @Test
    fun `marks run as FAILED and does not publish event when upload throws`() {
        doThrow(RuntimeException("S3 unavailable")).`when`(fileStorage).upload(any(), any(), any())

        assertThrows<RuntimeException> {
            useCase.execute(date, ByteArrayInputStream(content), content.size.toLong())
        }

        val runCaptor = argumentCaptor<ReconciliationRun>()
        verify(runRepository, times(2)).save(runCaptor.capture())

        assertEquals(RunStatus.FAILED, runCaptor.lastValue.status)
        verify(eventPublisher, never()).publish(any())
    }
}
