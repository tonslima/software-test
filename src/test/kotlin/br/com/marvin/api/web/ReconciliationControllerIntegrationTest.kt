package br.com.marvin.api.web

import br.com.marvin.api.application.port.FileStorage
import br.com.marvin.api.application.port.ReconciliationEventPublisher
import br.com.marvin.api.domain.model.ReconciliationResult
import br.com.marvin.api.domain.model.ReconciliationRun
import br.com.marvin.api.domain.vo.ReconciliationCategory
import br.com.marvin.api.domain.vo.RunStatus
import br.com.marvin.api.infrastructure.persistence.ReconciliationResultRepository
import br.com.marvin.api.infrastructure.persistence.ReconciliationRunRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReconciliationControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var runRepository: ReconciliationRunRepository

    @Autowired
    private lateinit var resultRepository: ReconciliationResultRepository

    @MockitoBean
    private lateinit var fileStorage: FileStorage

    @MockitoBean
    private lateinit var eventPublisher: ReconciliationEventPublisher

    @MockitoBean
    private lateinit var s3Client: software.amazon.awssdk.services.s3.S3Client

    @MockitoBean
    private lateinit var kafkaTemplate: org.springframework.kafka.core.KafkaTemplate<String, String>

    private val csvContent = "transaction_id,merchant_id,amount,currency,settled_at,processor_reference,status\n" +
        "550e8400-e29b-41d4-a716-446655440000,MERCH_001,152.30,BRL,2025-03-15T14:30:00Z,PS-2025-00012345,SETTLED"

    private val validDate = LocalDate.now(ZoneOffset.UTC).toString()

    @Test
    fun `returns 202 with runId on valid request`() {
        val file = MockMultipartFile("file", "settlement.csv", "text/csv", csvContent.toByteArray())

        mockMvc.perform(
            multipart("/reconciliations")
                .file(file)
                .param("referenceDate", validDate)
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.runId").isNotEmpty)
    }

    @Test
    fun `returns 400 when referenceDate is missing`() {
        val file = MockMultipartFile("file", "settlement.csv", "text/csv", csvContent.toByteArray())

        mockMvc.perform(
            multipart("/reconciliations")
                .file(file)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `returns 400 when referenceDate is malformed`() {
        val file = MockMultipartFile("file", "settlement.csv", "text/csv", csvContent.toByteArray())

        mockMvc.perform(
            multipart("/reconciliations")
                .file(file)
                .param("referenceDate", "invalid")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `returns 400 when referenceDate is in the future`() {
        val file = MockMultipartFile("file", "settlement.csv", "text/csv", csvContent.toByteArray())
        val futureDate = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString()

        mockMvc.perform(
            multipart("/reconciliations")
                .file(file)
                .param("referenceDate", futureDate)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `returns 400 when referenceDate is older than 90 days`() {
        val file = MockMultipartFile("file", "settlement.csv", "text/csv", csvContent.toByteArray())
        val oldDate = LocalDate.now(ZoneOffset.UTC).minusDays(91).toString()

        mockMvc.perform(
            multipart("/reconciliations")
                .file(file)
                .param("referenceDate", oldDate)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `returns 400 when file is missing`() {
        mockMvc.perform(
            multipart("/reconciliations")
                .param("referenceDate", validDate)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET results returns 404 when run does not exist`() {
        mockMvc.perform(get("/reconciliations/${UUID.randomUUID()}/results"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET results returns 202 when run is still pending`() {
        val run = createRun(RunStatus.PENDING)

        mockMvc.perform(get("/reconciliations/${run.id}/results"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.runStatus").value("PENDING"))
    }

    @Test
    fun `GET results returns 200 with paginated results for completed run`() {
        val run = createRun(RunStatus.COMPLETED)
        resultRepository.save(ReconciliationResult(
            run = run,
            transactionId = UUID.randomUUID(),
            category = ReconciliationCategory.MATCHED,
            processorAmount = BigDecimal("100.00"),
            internalAmount = BigDecimal("100.00"),
        ))
        resultRepository.save(ReconciliationResult(
            run = run,
            transactionId = UUID.randomUUID(),
            category = ReconciliationCategory.MISMATCHED,
            processorAmount = BigDecimal("200.00"),
            internalAmount = BigDecimal("150.00"),
        ))

        mockMvc.perform(get("/reconciliations/${run.id}/results"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.results.length()").value(2))
    }

    @Test
    fun `GET results filters by category`() {
        val run = createRun(RunStatus.COMPLETED)
        resultRepository.save(ReconciliationResult(
            run = run,
            transactionId = UUID.randomUUID(),
            category = ReconciliationCategory.MATCHED,
            processorAmount = BigDecimal("100.00"),
            internalAmount = BigDecimal("100.00"),
        ))
        resultRepository.save(ReconciliationResult(
            run = run,
            transactionId = UUID.randomUUID(),
            category = ReconciliationCategory.MISMATCHED,
            processorAmount = BigDecimal("200.00"),
            internalAmount = BigDecimal("150.00"),
        ))

        mockMvc.perform(get("/reconciliations/${run.id}/results?category=MISMATCHED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.results[0].category").value("MISMATCHED"))
    }

    @Test
    fun `GET results returns 400 when size exceeds maximum`() {
        val run = createRun(RunStatus.COMPLETED)

        mockMvc.perform(get("/reconciliations/${run.id}/results?size=201"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET stats returns 404 when run does not exist`() {
        mockMvc.perform(get("/reconciliations/${UUID.randomUUID()}/stats"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET stats returns 202 when run is still pending`() {
        val run = createRun(RunStatus.PENDING)

        mockMvc.perform(get("/reconciliations/${run.id}/stats"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.runStatus").value("PENDING"))
            .andExpect(jsonPath("$.runId").value(run.id.toString()))
    }

    @Test
    fun `GET stats returns 200 with category counts for completed run`() {
        val run = createRun(RunStatus.COMPLETED)
        resultRepository.save(ReconciliationResult(
            run = run,
            transactionId = UUID.randomUUID(),
            category = ReconciliationCategory.MATCHED,
            processorAmount = BigDecimal("100.00"),
            internalAmount = BigDecimal("100.00"),
        ))
        resultRepository.save(ReconciliationResult(
            run = run,
            transactionId = UUID.randomUUID(),
            category = ReconciliationCategory.MISMATCHED,
            processorAmount = BigDecimal("200.00"),
            internalAmount = BigDecimal("150.00"),
        ))

        mockMvc.perform(get("/reconciliations/${run.id}/stats"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.runStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.totalTransactions").value(2))
            .andExpect(jsonPath("$.discrepancyRate").value(50.0))
            .andExpect(jsonPath("$.categories.MATCHED").value(1))
            .andExpect(jsonPath("$.categories.MISMATCHED").value(1))
            .andExpect(jsonPath("$.categories.UNRECONCILED_PROCESSOR").value(0))
            .andExpect(jsonPath("$.categories.UNRECONCILED_INTERNAL").value(0))
    }

    private fun createRun(status: RunStatus): ReconciliationRun {
        val run = ReconciliationRun(
            id = UUID.randomUUID(),
            referenceDate = LocalDate.now(ZoneOffset.UTC).minusDays(1),
            s3Key = "reconciliations/runs/test/input.csv",
        )
        run.status = status
        return runRepository.save(run)
    }
}
