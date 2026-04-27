package com.paymentplatform.orderservice.infrastructure.kafka;

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
 * Kafka producer infrastructure configuration for order-service.
 *
 * <p><strong>Key settings and their rationale:</strong></p>
 * <ul>
 *   <li>{@code acks=all} — the leader broker waits for all in-sync replicas to acknowledge
 *       the write before confirming success, providing the strongest durability guarantee.</li>
 *   <li>{@code retries=3} — the producer retries transient failures up to 3 times before
 *       surfacing the error to the caller (which then marks the outbox event for retry).</li>
 *   <li>{@code enable.idempotence=true} — prevents duplicate Kafka records if a producer retry
 *       occurs after the broker already committed a message. Requires {@code acks=all}.</li>
 *   <li>{@code max.in.flight.requests.per.connection=1} — combined with idempotence, ensures
 *       strict per-partition ordering even in the presence of retries.</li>
 *   <li>{@code observationEnabled=true} — integrates with Micrometer/OpenTelemetry to inject
 *       trace context into Kafka message headers for distributed tracing across services.</li>
 * </ul>
 *
 * <p>Both key and value are plain strings. The outbox processor serialises domain event payloads
 * to JSON before calling the producer; no schema registry or Avro serialiser is needed.</p>
 */
@Configuration
public class KafkaProducerConfig {

    /** Kafka bootstrap servers from {@code spring.kafka.bootstrap-servers} in application.yml. */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates the producer factory with idempotent, strongly-durable settings.
     *
     * @return configured {@link ProducerFactory} used by the {@link KafkaTemplate}
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
     * Creates the {@link KafkaTemplate} used by {@code KafkaEventPublisher} to send messages.
     * OTel observation is enabled so Micrometer auto-instruments every send call.
     *
     * @return {@link KafkaTemplate} configured with the producer factory and tracing support
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true);
        return template;
    }
}
