package br.com.marvin.api.web

import br.com.marvin.api.infrastructure.messaging.ReconciliationEventPublisher
import br.com.marvin.api.infrastructure.storage.ReconciliationFileStorage
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReconciliationControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var fileStorage: ReconciliationFileStorage

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
}
