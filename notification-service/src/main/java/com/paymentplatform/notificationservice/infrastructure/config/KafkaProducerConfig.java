package com.paymentplatform.notificationservice.infrastructure.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer infrastructure configuration for notification-service.
 *
 * <p>Notification-service is primarily a consumer. The producer here is used exclusively
 * for DLQ publishing — when a notification exhausts all retry attempts, {@code DlqPublisher}
 * writes the failed event payload to {@code notification.dlq} for offline investigation.</p>
 *
 * <p>Uses the same strong-durability settings as all other services: {@code acks=all},
 * {@code retries=3}, {@code enable.idempotence=true}, {@code max.in.flight=1}.
 * OTel observation is enabled for distributed tracing.</p>
 */
@Configuration
public class KafkaProducerConfig {

    /** Kafka bootstrap servers from {@code spring.kafka.bootstrap-servers} in application.yml. */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates the producer factory with idempotent, strongly-durable settings.
     * Used only for DLQ message publishing.
     *
     * @return configured {@link ProducerFactory}
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates the {@link KafkaTemplate} used by {@code DlqPublisher} with OTel observation.
     *
     * @return {@link KafkaTemplate} with tracing support enabled
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true);
        return template;
    }
}
