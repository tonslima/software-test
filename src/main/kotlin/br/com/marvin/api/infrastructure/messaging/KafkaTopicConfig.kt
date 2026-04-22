package br.com.marvin.api.infrastructure.messaging

import br.com.marvin.api.infrastructure.messaging.publisher.KafkaAlertEventPublisher
import br.com.marvin.api.infrastructure.messaging.publisher.KafkaReconciliationEventPublisher
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaTopicConfig {

    @Bean
    fun reconciliationRequestedTopic(): NewTopic =
        TopicBuilder.name(KafkaReconciliationEventPublisher.TOPIC).partitions(1).replicas(1).build()

    @Bean
    fun reconciliationEventsTopic(): NewTopic =
        TopicBuilder.name(KafkaAlertEventPublisher.TOPIC).partitions(1).replicas(1).build()
}
