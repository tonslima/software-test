package br.com.marvin.api

import br.com.marvin.api.infrastructure.messaging.ReconciliationEventPublisher
import br.com.marvin.api.infrastructure.storage.ReconciliationFileStorage
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
@ActiveProfiles("test")
class ApplicationTests {

	@MockitoBean
	private lateinit var fileStorage: ReconciliationFileStorage

	@MockitoBean
	private lateinit var eventPublisher: ReconciliationEventPublisher

	@MockitoBean
	private lateinit var s3Client: software.amazon.awssdk.services.s3.S3Client

	@MockitoBean
	private lateinit var kafkaTemplate: org.springframework.kafka.core.KafkaTemplate<String, String>

	@Test
	fun contextLoads() {
	}

}
