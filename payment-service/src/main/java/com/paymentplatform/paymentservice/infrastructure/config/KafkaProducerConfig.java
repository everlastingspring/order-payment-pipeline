package com.paymentplatform.paymentservice.infrastructure.config;

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
 * Kafka producer infrastructure configuration for payment-service.
 *
 * <p>Identical settings to order-service: {@code acks=all}, {@code retries=3},
 * {@code enable.idempotence=true}, {@code max.in.flight.requests=1}.
 * These provide strong durability and exactly-once Kafka delivery semantics.
 * OTel observation is enabled for distributed tracing.</p>
 *
 * <p>Payment-service produces {@code payment.completed} and {@code payment.failed}
 * events via the transactional outbox pattern.</p>
 */
@Configuration
public class KafkaProducerConfig {

    /** Kafka bootstrap servers from {@code spring.kafka.bootstrap-servers} in application.yml. */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates the producer factory with idempotent, strongly-durable settings.
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
     * Creates the {@link KafkaTemplate} used by {@code KafkaEventPublisher} with OTel observation.
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
